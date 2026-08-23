# 진도

> **문서 지도** — 이 파일이 시작점이다.
> [설계문서](./superpowers/specs/2026-08-18-commerce-lab-design.md) · [협업 규칙](../CLAUDE.md) ·
> [마일스톤](./milestones/) · [ADR](./adr/) · [용어 사전](./glossary.md) · [Kotlin 노트](./kotlin-notes.md) ·
> [인프라](../infra/README.md) · [README](../README.md)

세션 시작 시 Claude가 가장 먼저 읽는 파일이다. 현재 위치와 다음 할 일을 여기서 판단한다.

## 현재 마일스톤

**M1 — 주문 코어와 동시성 제어** — 1단계(락 없이). 도메인·Product 어댑터 완료. Order 어댑터와 재고가 남았다. 브랜치 `m1-step1-no-lock`

작업지시서: [M1-order-core.md](./milestones/M1-order-core.md) — 할 일 목록은 그 문서 §13.

---

## 인수인계 (2026-08-23 기준)

다른 머신에서 이어받을 때 이 절만 읽으면 된다. 브랜치는 `m1-step1-no-lock`.

### 지금 딱 멈춘 지점

M1 §13-5. **1단계 구현이 끝났고 오버셀을 관측했다.** 남은 것은 k6 수치와 리뷰다.

```bash
cd backend
./gradlew :bootstrap:test
# ArchUnit 7건 PASSED, HealthIntegrationTest 3건 PASSED
# ConcurrentOrderIntegrationTest 1건 FAILED  ← 1단계에서는 이게 정상이다
#   "성공 건수가 재고를 넘으면 오버셀이다. 초과분 = 50건 ==> expected: <50> but was: <100>"
```

**이 실패가 관측 결과다.** 2단계에서 통과로 바뀐다.

### 1단계 관측 결과 (M1 §8 표 1행)

| 항목 | 값 |
|---|---|
| 동시 요청 / 재고 | 100 / 50 |
| 성공 주문 | **100** |
| 오버셀 | **50** |
| DB `reserved` | **21** ← 갱신 손실. 돌릴 때마다 다르다 |
| 5xx | 0 |
| TPS / p95 / p99 | **미측정 — k6 남음** |

두 가지 고장이 동시에 일어났다.

- **오버셀** — 낡은 값을 읽고 검사해서 통과시킨다. 재고 50에 100건이 팔렸다
- **갱신 손실** — `SET reserved = 읽은값 + 1`이 그 사이 남이 쓴 값을 덮어쓴다.
  100건을 팔았는데 장부에는 21로 적혔다. **판 사실 자체가 사라졌다**

`DB reserved` 컬럼은 이 관측 때문에 §8 표에 나중에 추가한 것이다.

### 실패 경로 실측 (재고 1, 순차 5요청)

```
성공        201  Location + PlaceOrder 본문
재고부족    409  application/problem+json  type=out-of-stock
상품없음    404  type=product-not-found
잘못된수량  400  type=invalid-quantity
빈주문      400  type=empty-order
DB: orders=1  order_lines=1  reserved=1   ← 실패한 4건은 아무것도 쓰지 않았다
```

### 이번 세션에 생긴 것 / 바뀐 것

| 파일 | 누가 | 내용 |
|---|---|---|
| `domain/Inventory.kt` | 사용자 | 신규. `total`/`reserved`/`version` + `addReserved(): DomainResult` |
| `domain/InventoryRepository.kt` | 사용자 | 조회와 갱신을 **일부러 분리** — 그 틈이 오버셀 통로다 |
| `infrastructure/InventoryEntity.kt` · `InventoryJpaRepository.kt` · `InventoryRepositoryAdapter.kt` | 사용자 | 신규 |
| `infrastructure/OrderRepositoryAdapter.kt` · `OrderLineRepository.kt` | 사용자 | 신규. orders + order_lines 두 테이블에 쓴다 |
| `application/OrderPlacementService.kt` | **Claude** | 읽기·계산 / 쓰기 두 구간으로 재구성 |
| `bootstrap/web/order/OrderController.kt` | **Claude** | `ResponseEntity<Any>` + `@ApiResponses` + `fold` |
| `bootstrap/web/order/OrderProblems.kt` | **Claude** | 신규. `OrderError` → `ProblemDetail` 매핑표 |
| `bootstrap/web/ProblemDetailAdvice.kt` | **Claude** | 전면 재작성. 예상 못 한 예외만 500 |
| `bootstrap/web/DevController.kt` · `dto/DevResetRequest.kt` | **Claude** | 전면 재작성. products/inventories upsert + orders 삭제 |
| `bootstrap/build.gradle.kts` | **Claude** | `jackson-module-kotlin` 추가 |
| `docs/milestones/M1-order-core.md` | Claude | §4-2 신설, §8 표에 `DB reserved` 컬럼, §13 갱신 |

Claude가 손댄 것은 전부 사용자가 명시적으로 지시한 것이다.

### 이번에 밝혀진 함정 세 개 (전부 실측으로 확인)

**1. `jackson-module-kotlin`이 없으면 모든 `@RequestBody`가 400이다**

Kotlin은 생성자 파라미터 이름을 바이트코드에 남기지 않는다(`javap -v`에 `MethodParameters` 없음).
Boot가 기본 등록하는 `jackson-module-parameter-names`만으로는 data class를 만들 수 없다.

```
InvalidDefinitionException: Cannot construct instance of `PlaceOrderCommand`
  (no Creators, like default constructor, exist)
```

증상이 400이라 "요청 형식이 틀렸나" 쪽을 의심하게 만든다. 원인은 빌드 설정이었다.

**2. `@ExceptionHandler(RuntimeException::class)`는 계측기를 고장낸다**

이전 `ProblemDetailAdvice`가 모든 런타임 예외를 400으로 바꿨다.
NPE도 커넥션 풀 고갈도 400. 5xx 그래프는 평평하고,
통합 테스트의 `서버오류 == 0` 단언은 **영원히 통과했다.**

규칙: 잡을 예외는 좁게. 안 잡힌 것이 500으로 나가는 건 정상 동작이다.

**3. 실패를 값으로 돌려주면 트랜잭션이 롤백되지 않는다**

스프링은 **예외**를 보고 롤백을 결정한다. 반환값은 보지 않는다.
`DomainResult.failure`를 return 하는 것은 정상 종료라 그대로 커밋된다.
실측: 성공 1건인데 `orders=2` — 실패한 주문이 DB에 남았다.

해결은 `place()`를 **읽기·계산 구간**과 **쓰기 구간**으로 나눈 것.
모든 검증이 쓰기 전에 끝나므로 되돌릴 것이 없다.
(대안이던 `setRollbackOnly()`는 스프링 내부 API를 애플리케이션에 들인다)

이 함정은 `DomainResult`를 도입한 순간 예약돼 있었다. 자세한 내용은 M1 §4-2.

### ⚠ 다음 세션에서 이어서 이야기할 것 (미결)

**"실패를 경계에서 값으로 옮길 것인가, 예외로 되돌릴 것인가"** — 결론이 나지 않았다.

지금 컨트롤러는 `ResponseEntity<Any>` + `@ApiResponses` + `fold`로 **값으로** 옮긴다.
`DomainResult`를 도입한 결정과 일관되기 때문이다.

그런데 **Spring 진영의 주류는 예외 기반이다.** Spring 6이 `ErrorResponse`와
`ErrorResponseException`을 추가한 것도 그 방향이고, 무엇보다
**springdoc은 `@RestControllerAdvice` 핸들러의 `@ApiResponse`를 전역으로 적용한다** —
에러 응답을 어드바이스에 한 번만 적으면 모든 엔드포인트에 붙는다.
지금 방식의 "애노테이션이 장황하다"는 단점이 상당 부분 사라진다.

되돌리는 비용은 작다. **컨트롤러만** 바꾸면 되고 `OrderProblems.kt`의 매핑표는 재사용된다.

선택지 비교와 다음에 실측할 항목 3개는 **M1 §4-2의 "⚠ 다시 이야기할 것"** 에 정리해뒀다.
결론은 ADR-0005에 남긴다.

> 다른 세션에서 이 주제를 다시 꺼내 결정하고, 필요하면 컨트롤러를 고친다.
> 지금 코드가 틀린 것은 아니다 — 동작하고 테스트도 통과한다. 관례와 다를 뿐이다.

### 사용자가 할 일 — 순서대로

**1. k6로 TPS / p95 / p99 재기** ← 여기부터

```bash
docker compose -f infra/docker-compose.yml up -d
cd backend && ./gradlew :bootstrap:bootRun --args='--spring.profiles.active=dev'
# 다른 터미널
docker run --rm -i --network host grafana/k6 run - < infra/k6/order-concurrent.js
```

`http_req_duration`의 `p(95)`/`p(99)`와 `iterations`의 `/s`를 §8 표 1행에 적는다.

주의: `teardown`이 `GET /api/products`를 부르는데 그 엔드포인트가 없다.
`[최종 재고 조회 실패]` 로그만 찍히고 측정 자체는 정상이다. 만들거나 무시하거나.

**2. 통합 테스트를 두세 번 더 돌려 `DB reserved`가 흔들리는 범위 보기**

21은 한 번의 값일 뿐이다. **매번 다르다는 것 자체가 관측 결과다.**

**3. 리뷰 요청 후 2단계 진입 판단**

**4. M1 §13-1의 "2단계 전에 정리할 빚" 처리**

락을 넣는 순간 문제가 되는 것들이다. 목록은 M1 문서에 있다.

**5. ADR 4건** — 0002(마이그레이션) / 0004(만료 방식) / 0005(도메인 실패 표현) / 0006(도메인·영속성 분리)

### 미해결 지적 (리뷰에서 나온 것)

전부 M1 §13-1로 옮겼다. 1단계 관측을 막지는 않지만 2단계 전에 정리해야 한다.

| 심각도 | 위치 | 내용 |
|---|---|---|
| 중요 | `OrderPlacementService` | `Instant.now()` 직접 호출. 3단계 만료 테스트에서 시점을 고정할 수 없다 |
| 중요 | `domain/Product.kt` | `active` 없음. DDL 주석이 "도메인이 강제한다"고 선언한 규칙이 들어갈 자리가 없다 |
| 중요 | `domain/Product.kt` | `id`에 `UUID.randomUUID()` 기본값. DB에서 읽는 값에 기본값이 필요한가 |
| 중요 | `OrderRepositoryAdapter.save` | 직접 채운 `@Id` → Spring Data가 `merge()` → INSERT 앞에 SELECT가 한 번 더 |
| 중요 | `OrderLineRepository` | `JpaRepository<_, String>`인데 `@Id`는 `Long?` |
| 중요 | `InventoryEntity.version` | `@Version`이 없다. 지금은 그냥 정수 컬럼 |
| 중요 | `InventoryRepository.findByProductId` | 재고 행이 없으면 `IllegalArgumentException` → 500 |
| 사소 | `PlaceOrder.reservations` | 3단계 전까지 `emptyList()`. TODO 주석은 달아둠 |
| 사소 | 여러 파일 | 파일 끝 개행 없음 |

### Claude가 할 일

- k6 수치 해석, 2단계 진입 판단
- `domain`/`application`에 클래스가 찼으니 ArchUnit의 `allowEmptyShould(true)` 제거 — **아직 안 함**
- 프론트엔드는 2단계 통과 후

### 확정된 설계 결정

- **마이그레이션**: Flyway. 이력 테이블은 `public` 스키마 (모듈 스키마에 두면 M4 분리 때 딸려 간다)
- **선점 만료**: 주기 배치 단독. TTL 3분 / 주기 5초 / 1회 500건. Redis는 M1에서 안 쓴다
- **`orders.id`**: `varchar(100)`, 애플리케이션이 생성
- **도메인 실패**: `kotlin.Result` 대신 `common`의 `DomainResult<E, T>`
- **시간**: 애플리케이션이 소유. 도메인 함수가 `now: Instant`를 받고 `updatedAt`을 갱신한다
- **도메인 ≠ 엔티티**: 매핑 비용을 내고 분리한다. ArchUnit이 강제
- **취소 가능 상태**: CREATED, PAID만. SHIPPED/DELIVERED는 거부 (반품은 별도 프로세스)
- **모듈 경계와 FK**: 같은 모듈 안이면 FK 걸고 JOIN한다(`products`). 경계를 넘으면 값으로만
  들고 있는다(`orders.account_id` — `accounts`는 payment 스키마). M4 물리 분리를 위해서다
- **가격 스냅샷**: `order_lines.unit_amount`에 주문 시점 단가를 복사해 박는다.
  정규화를 깨는 대신 가격 이력 정확성을 얻는다
- **포트는 사실만 반환한다**: 그 사실이 실패인지는 application이 정한다.
  저장 실패는 도메인 실패가 아니라 사고 → 예외. `DomainResult`를 포트에 씌우지 않는다
- **`DomainResult`는 껍질째 위로 올라간다**: 벗기는 곳은 컨트롤러의 `fold` 한 군데
- **HTTP 실패 매핑** (2026-08-23): 경계에서도 예외로 되돌리지 않는다.
  `OrderError` → RFC 9457 `ProblemDetail`을 **값으로** 옮긴다. 표는 `OrderProblems.kt`.
  **400과 409를 가르는 기준은 "다시 시도해서 달라질 여지가 있는가"다.**
  컨트롤러 반환 타입은 `ResponseEntity<Any>` + `@ApiResponses` — 추론은 어차피 201도 못 맞춘다 (M1 §4-2)
- **예외는 "일어나면 버그인 것"에만**: `Inventory.addReserved`의 음수 검사가 그 예.
  재고 부족은 매일 일어나는 정상 상황이라 값으로 돌려준다
- ADR-0002 / 0004 / 0005 / 0006 작성 대기 — **사용자 몫**

### 환경 메모

- Gradle 9.3.0 / Kotlin 2.2.21 / Java 21 (`JAVA_HOME=~/.sdkman/candidates/java/current`)
- 인프라: `docker compose -f infra/docker-compose.yml up -d` (Docker가 꺼져 있으면 `open -a OrbStack`)
- Testcontainers가 Docker를 못 찾으면 테스트가 "환경 문제"로 실패한다. 코드 문제와 헷갈리지 말 것
- 마이그레이션을 고쳤으면 `down -v` 후 재기동. `public.flyway_schema_history`가 남으면 체크섬이 어긋난다
- DB에 psql로 직접 DDL을 넣지 말 것. Flyway 이력과 어긋나 `relation already exists`가 난다
- Kotlin 프로젝트에서 `@RequestBody`가 400이면 `jackson-module-kotlin`부터 의심할 것
- 남은 부채: CI의 `actions/checkout@v4`·`setup-node@v4`가 Node 20 타깃이라 경고

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
- [ ] 1단계: 락 없이 구현 → 오버셀 관측 — **구현 완료, 오버셀 관측 완료** (재고 50 / 동시 100 → 성공 100, 오버셀 50, DB reserved 21). k6 수치만 남았다
- [ ] 2단계: 낙관적 락 + 재시도 정책
- [ ] 3단계: 선점(HELD) + TTL, 만료/확정 경쟁 조건 처리
- [ ] 프론트: 상품 목록 / 주문 / 실시간 재고 (Claude)
- [ ] ADR 2건 이상 (사용자) — ADR-0002 마이그레이션 도구, ADR-0004 만료 구동 방식,
      ADR-0005 도메인 실패 표현, ADR-0006 도메인·영속성 분리
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
| 갱신 손실 (lost update) | M1 | `SET x = 읽은값 + 1`은 그 사이 남이 쓴 값을 덮어쓴다. 오버셀보다 고약하다 — 판 사실 자체가 장부에서 사라진다 |
| 실패를 값으로 vs 예외로 | M1 | "일어나면 버그"는 예외, "알려줘야 할 일"은 값. 스프링은 예외만 보고 롤백하므로, 값으로 돌려주면 실패해도 커밋된다 |
| RFC 9457 Problem Details | M1 | 에러 응답의 표준 형식. `type`은 분류(기계용), `detail`은 이번 건(사람용). 클라이언트가 `detail`을 파싱하면 설계 실패 |
| 400 vs 409 | M1 | "다시 시도해서 달라질 여지가 있는가". 400은 요청 자체가 틀린 것, 409는 지금 자원 상태와 충돌한 것 |
| 예외 핸들러의 범위 | M1 | 넓게 잡으면 계측기가 고장난다. `RuntimeException`을 400으로 바꾸자 5xx 그래프가 평평해지고 테스트가 영원히 통과했다 |

_나머지는 마일스톤을 마칠 때마다 사용자가 채운다._
