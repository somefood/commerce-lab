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
  const oversold = target.reserved - target.total;
  console.log(`[최종] total=${target.total} reserved=${target.reserved} available=${target.total - target.reserved}`);
  console.log(oversold > 0 ? `[오버셀] ${oversold}건 초과 판매됨` : `[오버셀] 0건`);
}
