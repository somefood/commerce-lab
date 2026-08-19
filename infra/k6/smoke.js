// k6는 로컬에 설치하지 않는다. Docker로 실행한다:
//   docker run --rm -i --network host grafana/k6 run - < infra/k6/smoke.js
//
// M1에서 이 파일을 확장해 동시 주문 부하를 걸고 오버셀을 재현한다.

import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 10,
  duration: '10s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const res = http.get('http://localhost:8080/api/health');
  check(res, {
    'status is 200': (r) => r.status === 200,
    'status field is UP': (r) => r.json('status') === 'UP',
  });
}
