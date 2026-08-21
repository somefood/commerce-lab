# 진도

> **문서 지도** — 이 파일이 시작점이다.
> [설계문서](./superpowers/specs/2026-08-18-commerce-lab-design.md) · [협업 규칙](../CLAUDE.md) ·
> [마일스톤](./milestones/) · [ADR](./adr/) · [인프라](../infra/README.md) · [README](../README.md)

세션 시작 시 Claude가 가장 먼저 읽는 파일이다. 현재 위치와 다음 할 일을 여기서 판단한다.

## 현재 마일스톤

**M1 — 주문 코어와 동시성 제어** — 1단계(락 없이). 도메인·Product 어댑터 완료. Order 어댑터와 재고가 남았다. 브랜치 `m1-step1-no-lock`

작업지시서: [M1-order-core.md](./milestones/M1-order-core.md) — 할 일 목록은 그 문서 §13.

---

## 인수인계 (2026-08-21 기준)

다른 머신에서 이어받을 때 이 절만 읽으면 된다. 브랜치는 `m1-step1-no-lock`.

### 지금 딱 멈춘 지점

M1 §13-4. **컴파일은 통과하고 단위·아키텍처 테스트도 전부 통과한다.**
다만 앱은 아직 못 뜬다 — `OrderRepository` 구현체가 없어서 `OrderPlacementService`
빈을 만들 수 없다.

```bash
cd backend
./gradlew compileKotlin                    # 통과
./gradlew :modules:order:order-core:test   # 17건 통과
./gradlew :bootstrap:archTest              # 7건 통과
./gradlew :bootstrap:test                  # 컨텍스트 로딩 실패 ← OrderRepository 빈 없음
```

아직 "오버셀 관측"이 아니다. 재고 로직 자체가 없다.

### 이번 세션에 생긴 것

| 파일 | 내용 |
|---|---|
| `domain/Product.kt` | 신규. `id` + `unitAmount: Money` |
| `domain/ProductRepository.kt` | 신규. `findByIds(ids): List<Product>` |
| `infrastructure/ProductJpaRepository.kt` | 신규 |
| `infrastructure/ProductRepositoryAdapter.kt` | 신규. **어댑터 표준형 — 나머지 둘은 이걸 따라간다** |
| `domain/OrderRepository.kt` | `save(placedOrder: Order): Order` |
| `application/OrderPlacementService.kt` | `place()` 흐름 배선. 재고 없음 |
| `bootstrap/.../OrderController.kt` | `place()`만 `fold`로 배선 |

### 사용자가 할 일 — 순서대로

**1. `OrderPlacementService`의 상품 조회 부분** ← 여기부터 시작

`findAllById`는 없는 id를 조용히 빼고 돌려준다. 3개 요청해도 2개가 올 수 있다.
지금 코드는 못 찾은 라인을 `if (product != null)`로 **그냥 건너뛴다.**

없는 상품 하나만 담아 주문하면 → `orderLines`가 비고 → `Order.place`가 `EmptyOrder`를
반환한다. 클라이언트는 "빈 주문을 보냈다"는 답을 받는다. 실제로는 상품을 보냈는데.
`OrderError.ProductNotFound`가 정의돼 있는데 코드 어디서도 안 쓰인다.

> 하나라도 못 찾으면 전체가 실패해야 한다. `mutableListOf`에 `add`하는 구조로는
> 그게 어렵다는 게 힌트다.

**2. `OrderRepositoryAdapter` 만들기** (`infrastructure/`)

`ProductRepositoryAdapter`가 본보기다. 근데 `Order`는 애그리거트라 세 군데서 걸린다.

- `Order.lines`가 `OrderEntity`엔 없다. `OrderLineEntity`가 `order_id`로 따로 산다
  → `save(order)` 한 번이 테이블 **두 개**에 써야 한다. `OrderLineJpaRepository`도 필요하고,
  FK가 걸려 있으니 저장 **순서**가 있다
- 타입이 안 맞는다: `OrderStatus` enum ↔ `String`, `placedAt` ↔ `created_at`,
  `OrderEntity.version`은 도메인에 없다 (2단계에서 `@Version`이 붙을 자리)
- `OrderLineEntity.id`가 `@GeneratedValue`인데 생성자 파라미터다. 새로 만들 때 뭘 넣나?

여기까지 되면 앱이 뜬다.

**3. 재고** — 1단계의 본 목적

- `domain/Inventory.kt` — `productId`, `total`, `reserved` + "이만큼 더 잡을 수 있나" 판단
- `domain/InventoryRepository.kt` — 지금 빈 인터페이스다
- `infrastructure/InventoryRepositoryAdapter.kt`
- `place()`에 재고 검사·차감 추가 → 실패면 `OrderError.OutOfStock`

**여기서 락을 걸지 않는다.** 읽기와 쓰기를 별도 메서드로 두어 그 사이가 벌어지게 둔다.
그 틈이 오버셀이 재현되는 통로이고, 1단계는 그걸 관측하는 게 목적이다.
(조건부 UPDATE 한 방으로 하면 DB가 막아버려서 관측할 게 없어진다)

**4. 컨트롤러 마무리**

- `place()`의 `onFailure`가 지금 `IllegalStateException`을 던진다. 도메인 실패를 예외로
  바꾸는 건 `DomainResult`를 만든 이유를 정면으로 되돌리는 것이다. 그리고 모든 에러가
  같은 응답이 된다 — 재고 부족과 없는 상품이 구분되지 않는다
- 상태 코드 기준: **클라이언트가 요청을 고치면 성공하나 → 4xx, 아니면 5xx**
  - `ProductNotFound` 404 / `InvalidQuantity`·`EmptyOrder` 400 / `InvalidStatusTransition` 409
  - `OutOfStock`은 409냐 422냐 — **내 결정**. 고르고 이유 한 줄 남길 것
  - `ConflictExhausted`는 409 (2단계 테스트가 명시)
- 실패 본문은 `ProblemDetail` (RFC 9457). `ResponseEntity<*>`는 구체 타입으로
  (star projection이면 springdoc이 스키마를 못 만들고 프론트 `gen:api`가 깨진다)
- `getOrder` / `confirmOrder`는 아직 `TODO()`
- `ProductController.getProducts()` 빈 몸통
- `OrderWebDto.kt`는 빈 클래스뿐 — `PlaceOrder`를 그대로 쓸 거면 삭제

**5. `DevController.reset` 본문** — 지금 빈 몸통

`{"productId":"p-sneaker","total":50}`을 받아서:
- `products`에 상품이 있게 한다 (없으면 `inventories`가 FK 때문에 안 들어간다)
- `inventories`를 `total=50, reserved=0`으로
- 이전 테스트가 남긴 주문·라인을 치운다

**6. 오버셀 관측하고 기록**

```bash
./gradlew :bootstrap:test --tests '*ConcurrentOrderIntegrationTest*'   # 실패해야 정상
docker run --rm -i --network host grafana/k6 run - < infra/k6/order-concurrent.js
```
- 실패 메시지의 `성공 건수 - 50`이 오버셀 건수다 → M1 §8 표 1행에 기록
- **여기서 멈추고 리뷰 요청.** 2단계로 바로 넘어가지 않는다

### 미해결 지적 (리뷰에서 나온 것)

| 심각도 | 위치 | 내용 |
|---|---|---|
| 치명 | `OrderPlacementService` 상품 조회 | 못 찾은 라인을 건너뛴다 → 위 1번 |
| 치명 | `OrderController.place` | 도메인 실패를 예외로 던진다 → 위 4번 |
| 중요 | `domain/Product.kt` | `id`에 `UUID.randomUUID()` 기본값. DB에서 읽어 채우는 값인데 기본값이 있으면 존재하지 않는 상품 id가 조용히 생긴다 |
| 중요 | `domain/Product.kt` | `active`가 없다. DDL 주석이 "비활성 상품으로 새 주문을 만들 수 없다는 규칙은 도메인이 강제한다"고 선언했는데 그 규칙이 들어갈 자리가 없다. 없는 상품과 팔지 않는 상품은 같은 실패인가? |
| 중요 | `OrderPlacementService` | `Instant.now()`를 서비스가 직접 부른다. 도메인에서 시간을 뺀 이유가 뭐였나? "10:00에 주문하면 updatedAt이 그 시각"을 테스트로 고정할 수 있나? |
| 중요 | `ProblemDetailAdvice` | 모든 `RuntimeException`을 400으로 바꾼다. NPE도 DB 커넥션 고갈도 400이 된다 → 통합 테스트의 `서버오류 == 0`이 버그가 있어도 통과한다 |
| 사소 | `OrderPlacementService` | `products.find{}`가 라인마다 리스트를 훑는다. `associateBy` |
| 사소 | `PlaceOrder.reservations = emptyList()` | 3단계 전까지는 거짓말이다. `// TODO 3단계` 남길 것 |
| 사소 | 여러 파일 | 파일 끝 개행 없음 (`\ No newline at end of file`) |

### 관측할 때 기억해둘 것 (지금 고치지 말 것)

`OrderEntity`의 `@Id`가 애플리케이션이 만든 문자열이라, Spring Data `save()`가
"새 것인지 기존 것인지" 몰라 **SELECT를 먼저 날린 뒤 INSERT**한다.
1단계 부하 테스트에서 쿼리 수를 보면 이게 보인다.

### Claude가 할 일

- 위 작업 리뷰
- `domain`/`application`에 클래스가 채워지면 ArchUnit 규칙의 `allowEmptyShould(true)` 제거
- 오버셀 수치 해석, 2단계 진입 판단
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
  정규화를 깨는 대신 가격 이력 정확성을 얻는다. 참조로 두면 상품 가격을 올리는 순간
  과거 주문 금액까지 바뀐다
- **포트는 사실만 반환한다**: 그 사실이 실패인지는 application이 정한다.
  저장 실패는 도메인 실패가 아니라 사고 → 예외. `DomainResult`를 포트에 씌우지 않는다
- **`DomainResult`는 껍질째 위로 올라간다**: 벗기는 곳은 HTTP로 바꾸는 컨트롤러의 `fold` 한 군데.
  중간에서 `getOrNull()`을 부르고 싶어지면 잘못 가고 있는 것
- ADR-0002 / 0004 / 0005 / 0006 작성 대기 — **사용자 몫**

### 환경 메모

- Gradle 9.3.0 / Kotlin 2.2.21 / Java 21 (`JAVA_HOME=~/.sdkman/candidates/java/current`)
- 인프라: `docker compose -f infra/docker-compose.yml up -d`
- 마이그레이션을 고쳤으면 `down -v` 후 재기동. `public.flyway_schema_history`가 남으면 체크섬이 어긋난다
- DB에 psql로 직접 DDL을 넣지 말 것. Flyway 이력과 어긋나 `relation already exists`가 난다
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
- [ ] 1단계: 락 없이 구현 → 오버셀 관측 — DDL·시그니처·도메인·Product 어댑터 완료. Order 어댑터·재고·컨트롤러가 남았다
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
