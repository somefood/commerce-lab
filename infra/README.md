# 로컬 인프라

> **관련 문서**
> [진도](../docs/PROGRESS.md) · [설계문서](../docs/superpowers/specs/2026-08-18-commerce-lab-design.md) ·
> [M1 마일스톤](../docs/milestones/M1-order-core.md) · [README](../README.md)

## 기동

    docker compose -f infra/docker-compose.yml up -d

## 접속 정보

| 서비스 | 주소 | 계정 |
|---|---|---|
| PostgreSQL | localhost:5432 | commerce / commerce (DB: commerce) |
| Redis | localhost:6379 | - |
| Redpanda (Kafka) | localhost:19092 | - |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3001 | admin / admin |

## 자주 쓰는 명령

    # 스키마 확인
    docker exec commerce-postgres psql -U commerce -d commerce -c "\dn"

    # 토픽 목록
    docker exec commerce-redpanda rpk topic list

    # 부하 테스트 (k6 설치 불필요)
    docker run --rm -i --network host grafana/k6 run - < infra/k6/smoke.js

    # 전체 정리 (데이터까지 삭제)
    docker compose -f infra/docker-compose.yml down -v

## 참고

Grafana는 3001 포트를 쓴다. 프론트엔드가 3000을 점유하기 때문이다.

Redpanda는 9092가 아닌 19092를 쓴다. 이 머신에 다른 Kafka 컨테이너가 9092를 점유하고 있어
충돌을 피한 것이다. M3에서 백엔드를 붙일 때 `spring.kafka.bootstrap-servers: localhost:19092`가 된다.
