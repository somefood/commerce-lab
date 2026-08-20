# 진도

> **문서 지도** — 이 파일이 시작점이다.
> [설계문서](./superpowers/specs/2026-08-18-commerce-lab-design.md) · [협업 규칙](../CLAUDE.md) ·
> [마일스톤](./milestones/) · [ADR](./adr/) · [인프라](../infra/README.md) · [README](../README.md)

세션 시작 시 Claude가 가장 먼저 읽는 파일이다. 현재 위치와 다음 할 일을 여기서 판단한다.

## 현재 마일스톤

**M1 — 주문 코어와 동시성 제어** — 1단계(락 없이). 도메인 완료, 영속성·REST 어댑터 차례. 브랜치 `m1-step1-no-lock`

작업지시서: [M1-order-core.md](./milestones/M1-order-core.md) — 할 일 목록은 그 문서 §13.

---

## 인수인계 (2026-08-20 기준)

다른 머신에서 이어받을 때 이 절만 읽으면 된다.

### 지금 딱 멈춘 지점

**도메인 모델이 끝났다** (`ee2d88a`). 도메인 테스트 17건 전부 통과, 전체 모듈 컴파일 통과.

```bash
cd backend && ./gradlew :modules:order:order-core:test   # 17/17
cd backend && ./gradlew classes testClasses              # 전체 컴파일
```

다음은 [M1 §13의 4번](./milestones/M1-order-core.md) — **락 없이 구현 + REST 어댑터**.
아직 Docker 없이 돌아가는 순수 단위 테스트만 있다. 4번부터 인프라가 필요하다.

### 사용자가 할 일 (다음 세션의 첫 작업)

**4번 — 락 없이 구현 + REST 어댑터**

1. 영속성 어댑터: 재고를 조회하고, 검사하고, UPDATE한다. **락을 걸지 않는다** (일부러 오버셀을 만든다)
2. `bootstrap`에 설계문서 §4.3의 엔드포인트 5개. `/api/dev/reset`은 `dev` 프로필에만
3. `@Transactional`은 `order-core`에만 붙인다. `bootstrap`에 붙이면 ArchUnit이 빌드를 깬다

**완료 판정:**
```bash
docker compose -f infra/docker-compose.yml up -d
cd backend && ./gradlew :bootstrap:bootRun
curl -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
  -d '{"accountId":"a1","lines":[{"productId":"p-sneaker","quantity":1}]}'
```
→ 주문이 만들어지면 끝.

그 뒤 5번(오버셀 관측)까지 하고 **6번에서 멈춰 리뷰를 받는다.** 2단계로 바로 넘어가지 않는다.

**병행 가능:** ADR-0002(마이그레이션 도구), ADR-0004(만료 구동 방식). 둘 다 합의는 끝났고 기록만 남았다.
`docs/adr/0001-modular-monolith.md`가 형식 예시다. 내 설명을 옮기지 말고 본인 언어로 쓸 것.

### Claude가 할 일

- 4번 구현 리뷰 (지적만, 고치지 않음)
- `ConcurrentOrderIntegrationTest`가 실제로 돌기 시작하면 결과 해석
- 프론트엔드는 2단계 통과 후

### 이미 끝난 것

| 항목 | 상태 | 비고 |
|---|---|---|
| 스키마 V1 | 완료 | Flyway 적용·제약 5종 동작 확인 |
| `DomainResult` | 완료 | `common` 모듈. 테스트 9건 통과 |
| `order-api` 시그니처 | 완료 | `DomainResult<OrderError, PlaceOrder>` |
| 실패 테스트 | 완료 | 도메인 17건 + 동시성 5건(4건은 `@Disabled`) |
| **Order 애그리거트** | **완료** | `ee2d88a`. `place`/`markPaid`/`cancel` + `OrderError` 8종 |
| k6 시나리오 | 완료 | `infra/k6/order-concurrent.js` (burst / throughput) |
| Grafana 대시보드 | 완료 | http://localhost:3001 → "M1 — 주문 동시성" |

### 확정된 설계 결정

- **마이그레이션**: Flyway. 이력 테이블은 `public` 스키마 (모듈 스키마에 두면 M4 분리 때 딸려 간다)
- **선점 만료**: 주기 배치 단독. TTL 3분 / 주기 5초 / 1회 500건. Redis는 M1에서 안 쓴다
- **`orders.id`**: `varchar(100)`, 애플리케이션이 생성. IDENTITY 아님
- **도메인 실패**: `kotlin.Result` 대신 `common`의 `DomainResult<E, T>`
- **시간**: 애플리케이션이 소유. 도메인 함수가 `now: Instant`를 받는다
- **상태 전이**: `else` 없는 `when(status)`. `OrderStatus`에 값이 추가되면 `markPaid`/`cancel`이
  컴파일 에러로 잡힌다 — 빠뜨린 결정을 컴파일러가 강제한다
- **취소 가능 상태**: `CREATED`, `PAID`만. `SHIPPED`/`DELIVERED`는 거부 (설계문서 §4.2)
- **에러는 사실을 담는다**: `InvalidStatusTransition.from`은 실제 현재 상태,
  `InvalidQuantity`는 실제 문제가 된 라인. 하드코딩된 예상값을 담지 않는다
- ADR-0002(마이그레이션 도구), ADR-0004(만료 방식) 작성 대기 — **사용자 몫**

### 환경 메모

- Gradle 9.3.0 / Kotlin 2.2.21 / Java 21 (`JAVA_HOME=~/.sdkman/candidates/java/current`)
- 인프라: `docker compose -f infra/docker-compose.yml up -d`
- 마이그레이션을 고쳤으면 `docker compose ... down -v` 후 재기동. 스키마만 지우면 `public.flyway_schema_history`가 남아 체크섬이 어긋난다
- 테스트가 이상하게 통과/실패하면 `--rerun-tasks`를 붙여본다. 증분 빌드가 옛 클래스를 물고 있을 수 있다
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
- [ ] 1단계: 락 없이 구현 → 오버셀 관측 — DDL·시그니처·도메인 완료. 영속성·REST 어댑터 차례
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
