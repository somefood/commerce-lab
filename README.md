# commerce-lab

백엔드 실력을 실전 수준으로 끌어올리기 위한 학습 협업 프로젝트.

주문 → 결제(복식부기 원장) → 배송 흐름을 Kotlin/Spring 모듈러 모놀리스로 구현하고,
동시성 제어 · 멱등성 · 이벤트 정합성 · 분산 트랜잭션을 단계적으로 다룬다.

## 역할

| 영역 | 담당 |
|---|---|
| 백엔드 프로덕션 코드 | 사람 |
| 백엔드 설계 · 실패 테스트 · 코드리뷰 | Claude |
| 프론트엔드 · 인프라 · CI | Claude |

상세 규칙은 [CLAUDE.md](./CLAUDE.md)에 있다.

## 문서

- [설계문서](./docs/superpowers/specs/2026-08-18-commerce-lab-design.md)
- [진도](./docs/PROGRESS.md)
- [ADR](./docs/adr/)

## 빠르게 실행하기

    # 1. 인프라
    docker compose -f infra/docker-compose.yml up -d

    # 2. 백엔드
    cd backend && ./gradlew :bootstrap:bootRun

    # 3. 프론트엔드 (새 터미널)
    cd frontend && npm install && npm run dev

http://localhost:3000 에서 백엔드 상태를 확인할 수 있다.

## 마일스톤

| | 주제 | 핵심 학습 |
|---|---|---|
| M0 | 스캐폴딩 | 모듈 경계 강제, 테스트 인프라 |
| M1 | 주문 코어 | 동시성 제어, 락, 선점과 TTL |
| M2 | 결제 원장 | 멱등성, 복식부기, 정합성 검증 |
| M3 | 아웃박스 | at-least-once, 컨슈머 멱등성, DLQ |
| M4 | 서비스 분리 | Saga, 보상 트랜잭션, 분리 비용 측정 |
