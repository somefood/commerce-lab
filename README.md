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

| 문서 | 무엇이 있나 | 누가 쓰나 |
|---|---|---|
| [진도 PROGRESS.md](./docs/PROGRESS.md) | 현재 마일스톤과 다음 할 일. **여기서 시작한다** | 양쪽 |
| [설계문서](./docs/superpowers/specs/2026-08-18-commerce-lab-design.md) | 아키텍처, 도메인 모델, 불변 규칙, 마일스톤 전체 계획 | Claude |
| [마일스톤 문서](./docs/milestones/) | 마일스톤별 작업지시서. 설계 근거 + 실패 테스트 + 할 일 체크리스트 | Claude |
| [ADR](./docs/adr/) | 결정 기록. 왜 그렇게 정했고 무엇을 버렸나 | 사용자 |
| [협업 규칙 CLAUDE.md](./CLAUDE.md) | 역할 분담, 리뷰 방식, 아키텍처 불변 규칙 | 양쪽 |
| [인프라 사용법](./infra/README.md) | 컨테이너 기동, 접속 정보, 자주 쓰는 명령 | Claude |
| [마이그레이션 규칙](./backend/bootstrap/src/main/resources/db/migration/README.md) | Flyway 파일명 규칙과 금지 사항 | Claude |
| [M0 실행계획](./docs/superpowers/plans/2026-08-18-m0-scaffolding.md) | 스캐폴딩 작업 기록 (완료) | Claude |

현재 진행 중: [M1 — 주문 코어와 동시성 제어](./docs/milestones/M1-order-core.md)

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
