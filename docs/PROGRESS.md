# 진도

> **문서 지도** — 이 파일이 시작점이다.
> [설계문서](./superpowers/specs/2026-08-18-commerce-lab-design.md) · [협업 규칙](../CLAUDE.md) ·
> [마일스톤](./milestones/) · [ADR](./adr/) · [인프라](../infra/README.md) · [README](../README.md)

세션 시작 시 Claude가 가장 먼저 읽는 파일이다. 현재 위치와 다음 할 일을 여기서 판단한다.

## 현재 마일스톤

**M1 — 주문 코어와 동시성 제어** — 1단계(락 없이) 구현 중. 브랜치 `m1-step1-no-lock`

작업지시서: [M1-order-core.md](./milestones/M1-order-core.md) — 할 일 목록은 그 문서 §13.

---

## 인수인계 (2026-08-20 기준)

다른 머신에서 이어받을 때 이 절만 읽으면 된다.

### 지금 딱 멈춘 지점

사용자가 `Order` 애그리거트 초안을 썼고(`ada6647`), **도메인 테스트 12건 중 4건이 실패한다.**

```bash
cd backend && ./gradlew :modules:order:order-core:test
```

```
OrderTest > 수량 오류는 문제가 된 라인을 가리킨다              FAILED
OrderTest > 상태 전이 실패는 실제 현재 상태를 알려준다          FAILED
OrderTest > 빈 주문의 실패 이유는 수량이나 선점과 구분된다      FAILED
OrderTest > 라인 중 하나만 수량이 0이어도 주문을 만들 수 없다   FAILED
```

이 실패는 의도된 것이다. 테스트가 스펙이고 구현이 아직 못 따라온 상태다.

### 사용자가 할 일 (다음 세션의 첫 작업)

`backend/modules/order/order-core/.../Order.kt`와 `order-api/.../OrderError.kt`를 고친다.

1. 안 쓰는 `import ch.qos.logback.core.spi.ErrorCodes` 삭제
2. `OrderError`에 빈 주문용 케이스 추가 (예: `EmptyOrder`). 지금은 `ReservationAlreadySettled`를 빌려 쓴다
3. `InvalidStatusTransition(val a, val b)` → 이름을 `from`, `to`로
4. `validateOrderLines2`가 `allMatch`를 쓴다 → 라인 하나만 잘못돼도 거부되어야 한다 (`any`)
5. `InvalidQuantity("p-1", 0)` 하드코딩 제거 → `firstOrNull`로 문제가 된 라인을 찾아 담는다
6. `markPaid`/`cancel`을 `when(status)`로 바꾸고, 실패 시 `from`에 실제 `status`를 담는다
7. `cancel`이 SHIPPED/DELIVERED에서도 성공한다 → 설계문서 §4.2 상태머신과 맞출지 결정
8. `markPaid`/`cancel`이 `now`를 받고도 안 쓴다 → `updated_at`을 누가 채울지 결정

12건이 전부 초록이 되면 M1 §13의 3번이 끝난다. 그 다음이 4번(영속성 + REST 어댑터)이다.

### Claude가 할 일

- 위 수정에 대한 리뷰 (지적만, 고치지 않음)
- 4번 진행 시 `ConcurrentOrderIntegrationTest`가 실제로 돌기 시작하면 그 결과 해석
- 프론트엔드는 2단계 통과 후

### 이미 끝난 것

| 항목 | 상태 | 비고 |
|---|---|---|
| 스키마 V1 | 완료 | Flyway 적용·제약 5종 동작 확인 |
| `DomainResult` | 완료 | `common` 모듈. 테스트 9건 통과 |
| `order-api` 시그니처 | 완료 | `DomainResult<OrderError, PlaceOrder>` |
| 실패 테스트 | 완료 | 도메인 12건 + 동시성 5건(4건은 `@Disabled`) |
| k6 시나리오 | 완료 | `infra/k6/order-concurrent.js` (burst / throughput) |
| Grafana 대시보드 | 완료 | http://localhost:3001 → "M1 — 주문 동시성" |

### 확정된 설계 결정

- **마이그레이션**: Flyway. 이력 테이블은 `public` 스키마 (모듈 스키마에 두면 M4 분리 때 딸려 간다)
- **선점 만료**: 주기 배치 단독. TTL 3분 / 주기 5초 / 1회 500건. Redis는 M1에서 안 쓴다
- **`orders.id`**: `varchar(100)`, 애플리케이션이 생성. IDENTITY 아님
- **도메인 실패**: `kotlin.Result` 대신 `common`의 `DomainResult<E, T>`
- **시간**: 애플리케이션이 소유. 도메인 함수가 `now: Instant`를 받는다
- ADR-0002(마이그레이션 도구), ADR-0004(만료 방식) 작성 대기 — **사용자 몫**

### 환경 메모

- Gradle 9.3.0 / Kotlin 2.2.21 / Java 21 (`JAVA_HOME=~/.sdkman/candidates/java/current`)
- 인프라: `docker compose -f infra/docker-compose.yml up -d`
- 마이그레이션을 고쳤으면 `docker compose ... down -v` 후 재기동. 스키마만 지우면 `public.flyway_schema_history`가 남아 체크섬이 어긋난다
- 남은 부채: CI의 `actions/checkout@v4`·`setup-node@v4`가 Node 20 타깃이라 경고가 뜬다 (동작에는 영향 없음)

---

## 마일스톤 체크리스트

### M0 — 스캐폴딩 (Claude)
- [x] Gradle 멀티모듈 뼈대
- [x] ArchUnit 아키텍처 규칙
- [x] Docker Compose 인프라
- [x] Boot 앱 + Testcontainers 통합 테스트
- [x] 프론트엔드 뼈대 + OpenAPI 타입 생성
- [x] CI + 문서

### M1 — 주문 코어와 동시성 제어 (사용자 구현) — [작업지시서](./milestones/M1-order-core.md)
- [x] 설계문서 작성 (Claude) — [M1-order-core.md](./milestones/M1-order-core.md)
- [ ] 1단계: 락 없이 구현 → 오버셀 관측 — DDL·시그니처·실패 테스트 완료, 도메인 구현 중
- [ ] 2단계: 낙관적 락 + 재시도 정책
- [ ] 3단계: 선점(HELD) + TTL, 만료/확정 경쟁 조건 처리
- [ ] 프론트: 상품 목록 / 주문 / 실시간 재고 (Claude)
- [ ] ADR 2건 이상 (사용자) — ADR-0002 마이그레이션 도구, ADR-0004 만료 구동 방식
- [ ] 회고

### M2 — 결제 원장과 멱등성 (사용자 구현)
- [ ] 미시작

### M3 — 트랜잭셔널 아웃박스와 이벤트 (사용자 구현)
- [ ] 미시작

### M4 — 물리 분리와 Saga (사용자 구현)
- [ ] 미시작

## 배운 개념

마일스톤을 마칠 때마다 사용자가 채운다. 이직 서류의 원자료가 된다.

| 개념 | 마일스톤 | 한 줄 요약 |
|---|---|---|
| | | |
