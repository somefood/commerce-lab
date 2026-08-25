// M1 동시 주문 부하 시나리오.
//
// 실행:
//   docker run --rm -i --network host grafana/k6 run - < infra/k6/order-concurrent.js
//   docker run --rm -i grafana/k6 run -e BASE_URL=http://host.docker.internal:8080 - < infra/k6/order-concurrent.js
//     (host 네트워킹이 안 먹는 환경에서는 두 번째 형태를 쓴다)
//
// 시나리오 두 개를 환경변수로 고른다.
//   -e SCENARIO=burst       기본. 재고 50에 100요청을 동시에 던져 오버셀을 관측한다
//   -e SCENARIO=throughput  재고를 크게 잡고 30초간 지속 부하로 TPS/p99를 잰다
//
// 왜 나눴나: 오버셀 관측과 처리량 측정은 요구가 반대다. 오버셀은 "재고보다 요청이 많은"
// 상황이 필요하고, 처리량은 "재고 부족으로 인한 조기 실패가 없는" 상황이 필요하다.
// 한 시나리오로 둘 다 재려 하면 후반부가 전부 품절 응답이 되어 TPS가 왜곡된다.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO || 'burst';
const PRODUCT_ID = __ENV.PRODUCT_ID || 'p-sneaker';
const STOCK = SCENARIO === 'burst' ? 50 : 1000000;

const succeeded = new Counter('order_succeeded');
const outOfStock = new Counter('order_out_of_stock');
const conflict = new Counter('order_conflict');
const serverError = new Counter('order_server_error');

export const options =
  SCENARIO === 'burst'
    ? {
        // 100개 VU가 각자 1건씩. 동시에 출발시키는 것이 목적이므로 반복하지 않는다.
        scenarios: {
          burst: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 100,
            maxDuration: '60s',
          },
        },
        // 여기서 임계값을 걸지 않는다. 1단계에서는 실패(오버셀)를 관측하는 것이 목적이다.
      }
    : {
        scenarios: {
          throughput: {
            executor: 'constant-vus',
            vus: 100,
            duration: '30s',
          },
        },
        thresholds: {
          // 참고용. 넘겨도 실행은 계속된다 — 수치를 보는 것이 목적이다
          http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        },
        // ⚠ TPS는 요약의 `iterations ... /s` 를 그대로 쓰면 안 된다.
        // 그 값의 분모는 setup + 시나리오 + teardown 전체 시간이다.
        // setup의 dev/reset이 이전 실행에서 쌓인 주문을 지우느라 길어지면
        // 시나리오는 그대로인데 TPS만 떨어진 것처럼 보인다.
        // 실측(2026-08-25): 총 48.2초 중 시나리오는 30초.
        //   요약 표시 542/s  vs  실제 26,127 / 30s = 871/s
        // **TPS = iterations 개수 ÷ 아래 duration** 으로 직접 계산할 것.
      };

export function setup() {
  const res = http.post(
    `${BASE_URL}/api/dev/reset`,
    JSON.stringify({ productId: PRODUCT_ID, total: STOCK }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (res.status !== 200 && res.status !== 204) {
    throw new Error(`재고 초기화 실패 (${res.status}). dev 프로필로 백엔드를 띄웠는지 확인할 것: ${res.body}`);
  }
  return { productId: PRODUCT_ID, stock: STOCK };
}

export default function (data) {
  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({
      accountId: `acc-${__VU}`,
      lines: [{ productId: data.productId, quantity: 1 }],
    }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/orders' } },
  );

  if (res.status === 200 || res.status === 201) {
    succeeded.add(1);
  } else if (res.status === 409) {
    // 재고 부족 / 충돌. 서버가 어떤 코드로 구분하는지는 구현자가 정한다.
    // RFC 9457 Problem Details의 type 필드로 나눈다면 여기서 그걸 읽어 분기할 것.
    const body = res.body || '';
    if (body.includes('conflict') || body.includes('Conflict')) conflict.add(1);
    else outOfStock.add(1);
  } else if (res.status >= 500) {
    serverError.add(1);
  }

  check(res, {
    '5xx가 아니다': (r) => r.status < 500,
  });
}

export function teardown(data) {
  // 애플리케이션 카운터를 믿지 않는다. 최종 재고를 서버에서 직접 확인한다.
  const res = http.get(`${BASE_URL}/api/products`);
  if (res.status !== 200) {
    console.log(`최종 재고 조회 실패 (${res.status})`);
    return;
  }
  const products = res.json();
  const target = (Array.isArray(products) ? products : []).find((p) => p.productId === data.productId);
  if (!target) {
    console.log(`상품 ${data.productId}를 찾지 못했다`);
    return;
  }
  console.log(`[최종] total=${target.total} reserved=${target.reserved} available=${target.total - target.reserved}`);

  // reserved로 오버셀을 판정하지 않는다. 2026-08-25에 이 로직이 거짓 안심을 냈다 —
  // 재고 50에 100건이 팔린 실행에서 reserved가 11로 찍혔고, reserved - total 이 음수라
  // "[오버셀] 0건"을 출력했다. **오버셀이 50건 난 실행에서 계측기가 이상 없음이라고 말했다.**
  //
  // 원인은 갱신 손실(lost update)이다. 락 없이 `reserved = 읽은값 + n`을 쓰면
  // 동시 트랜잭션이 서로의 값을 덮어써서 reserved가 실제 판매량보다 훨씬 낮게 남는다.
  // 즉 **오버셀을 재려던 눈금이 다른 고장 때문에 망가져 있다.**
  //
  // 오버셀은 "성공 응답 수 > 재고"로 판정해야 한다. 그 값은 order_succeeded 카운터에 있고
  // teardown에서는 읽을 수 없으므로, 여기서는 판정하지 않고 무엇을 봐야 하는지만 알린다.
  const oversold = target.reserved - target.total;
  if (oversold > 0) {
    console.log(`[오버셀] 확정 — reserved가 total을 ${oversold} 넘었다`);
  } else {
    console.log(`[오버셀] 이 값으로는 판정할 수 없다.`);
    console.log(`         reserved(${target.reserved}) <= total(${target.total})이지만, 갱신 손실이 있으면`);
    console.log(`         reserved 자체가 실제 판매량보다 낮게 남는다. 위 요약의 order_succeeded와`);
    console.log(`         재고(${target.total})를 비교할 것. order_succeeded > ${target.total} 이면 오버셀이다.`);
  }

  // 갱신 손실은 별도 신호다. 정상이라면 reserved == 성공 주문 수여야 한다.
  if (target.reserved < target.total) {
    console.log(`[갱신 손실] reserved가 total보다 작다. 성공 주문 수와 비교해 손실량을 확인할 것`);
  }
}
