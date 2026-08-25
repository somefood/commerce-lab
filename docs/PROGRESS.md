# 진도

> **문서 지도** — 이 파일이 시작점이다.
> [설계문서](./superpowers/specs/2026-08-18-commerce-lab-design.md) · [협업 규칙](../CLAUDE.md) ·
> [마일스톤](./milestones/) · [ADR](./adr/) · [용어 사전](./glossary.md) · [Kotlin 노트](./kotlin-notes.md) ·
> [인프라](../infra/README.md) · [README](../README.md)

세션 시작 시 Claude가 가장 먼저 읽는 파일이다. 현재 위치와 다음 할 일을 여기서 판단한다.

## 현재 마일스톤

**M1 — 주문 코어와 동시성 제어** — 1단계(락 없이) 구현·관측 완료. k6 수치와 리뷰가 남았다. 브랜치 `m1-step1-no-lock`

작업지시서: [M1-order-core.md](./milestones/M1-order-core.md) — 할 일 목록은 그 문서 §13.

---

## 인수인계 (2026-08-25 기준)

다른 머신에서 이어받을 때 이 절만 읽으면 된다. 브랜치는 `m1-step1-no-lock`.

### 지금 딱 멈춘 지점

M1 §13-5 **완료.** 1단계 구현·오버셀 관측·k6 측정이 전부 끝났다.
남은 것은 리뷰와 §13-1의 빚 정리, 그리고 2단계 진입 판단이다.

그리고 8/24에 **도메인 실패 표현을 `DomainResult`(값)에서 예외로 전면 전환했다.**
사용자 지시로 Claude가 한 커밋에 몰아서 했다 — `git show`로 통째로 보고 직접 다시 짜보는 게 목적이다.

```bash
cd backend && ./gradlew build
# ArchUnit 8건 PASSED   ← order-api 프레임워크 차단 규칙이 새로 생겼다
# OrderTest 17건 PASSED
# OrderRollbackIntegrationTest 3건 PASSED   ← 이번 전환의 증거
# HealthIntegrationTest 3건 PASSED
# ConcurrentOrderIntegrationTest 1건 FAILED  ← 1단계에서는 이게 정상이다
#   "성공 건수가 재고를 넘으면 오버셀이다. 초과분 = 50건 ==> expected: <50> but was: <100>"
```

### 1단계 관측 결과 (M1 §8 표 1행) — 전환 후에도 동일

| 항목 | 값 |
|---|---|
| 동시 요청 / 재고 | 100 / 50 |
| 성공 주문 | **100** |
| 오버셀 | **50** |
| DB `reserved` | **8~21** ← 갱신 손실. 5회 돌린 범위 |
| 5xx | 0 |
| TPS / p95 / p99 | **845.6/s / 143.67ms / 164.39ms** (throughput 시나리오) |

두 가지 고장이 동시에 일어났다.

- **오버셀** — 낡은 값을 읽고 검사해서 통과시킨다. 재고 50에 100건이 팔렸다
- **갱신 손실** — `SET reserved = 읽은값 + 1`이 그 사이 남이 쓴 값을 덮어쓴다.
  100건을 팔았는데 장부에는 21로 적혔다. **판 사실 자체가 사라졌다**

### k6 측정에서 나온 가장 큰 것 (2026-08-24)

`throughput` 시나리오 — 재고를 100만으로 잡아 **재고 부족이 하나도 안 나는** 상황이다.

```
성공 주문    25,466
DB reserved   2,842     ← 88.8%가 장부에서 증발
5xx 0건 · 실패율 0% · p95 143.67ms · p99 164.39ms
```

**계기판은 전부 초록불인데 장부의 88%가 사라져 있다.**

두 고장의 성격이 다르다는 게 여기서 드러난다.
- **오버셀**은 재고 한도가 상한이라 초과분이 50으로 결정론적이다
- **갱신 손실**은 상한이 없다. 경합이 있는 만큼 계속 커진다

오버셀은 "재고가 모자랄 때"만 보이지만, **갱신 손실은 경합만 있으면 언제나 일어난다.**
그리고 응답만 봐서는 절대 안 보인다 — DB를 직접 열어야 한다.
`DB reserved` 컬럼을 §8 표에 나중에 추가한 판단이 여기서 증명됐다.

전체 수치는 [M1 §8의 "1단계 실측"](./milestones/M1-order-core.md).

### 실패 경로 실측 (재고 1, 순차 요청) — 응답은 전환 전후가 동일하다

```
성공        201  Location + PlaceOrder 본문
재고부족    409  application/problem+json  type=out-of-stock
상품없음    404  type=product-not-found
잘못된수량  400  type=invalid-quantity
빈주문      400  type=empty-order
재고행없음  500  type=internal-error   ← IllegalArgumentException. "일어나면 버그" 경로
DB: orders=1  order_lines=1  reserved=1   ← 실패한 5건은 아무것도 쓰지 않았다
```

---

## 8/24 전환: DomainResult → 예외

### 왜 뒤집었나

**롤백 하나 때문이다.** 스프링은 예외를 보고 롤백을 결정하고 반환값은 보지 않는다.
값으로 실패를 돌려주면 실패해도 커밋되므로, 서비스를 "읽기 구간 / 쓰기 구간"으로 갈라
쓰기 전에 모든 검증을 끝내야 했다. M2 원장(append-only)에서는 그 규율이 성립하지 않는다.
나중에 갈수록 시그니처가 더 많이 흔들리므로 M1에서 미리 갈아탔다.

springdoc 이득은 **따라온 것이지 이유가 아니다.** ADR-0005에서 순서를 섞지 말 것.

### 무엇이 바뀌었나

| 파일 | 내용 |
|---|---|
| `order-api/OrderException.kt` | 신규. `OrderError.kt` 삭제. `sealed class ... : RuntimeException(msg, null, false, false)` |
| `order-api/OrderPlacement.kt` | `place(cmd): PlaceOrder` — 반환 타입에서 `DomainResult` 제거. `@throws` KDoc |
| `domain/Order.kt` | `place`/`markPaid`/`cancel`이 `Order`를 반환하고 실패는 던진다 |
| `domain/Inventory.kt` | `addReserved(n): Inventory`. 음수는 `IllegalArgumentException`, 재고부족은 `OutOfStock` |
| `application/OrderPlacementService.kt` | **읽기/쓰기 분할을 풀었다.** 주문을 먼저 저장하고 재고를 나중에 검사한다 |
| `common/DomainResult.kt` + 테스트 | **삭제** |
| `bootstrap/web/ProblemDetailAdvice.kt` | `@ExceptionHandler(OrderException::class)` 추가. `@ApiResponses`가 여기로 이사 |
| `bootstrap/web/order/OrderProblems.kt` | `toResponse()` → `toProblemDetail()`. 매핑표 내용은 그대로 |
| `bootstrap/web/order/OrderController.kt` | `fold` 제거. `ResponseEntity<Any>` → `ResponseEntity<PlaceOrder>`. 애노테이션 23줄 → 1줄 |
| `order-core/test/OrderTest.kt` | 17건 전부 `assertFailsWith` + 필드 단언으로 |
| `bootstrap/test/OrderRollbackIntegrationTest.kt` | **신규 3건.** 이번 전환의 증거 |
| `bootstrap/test/ArchitectureTest.kt` | `order-api는 어떤 프레임워크도 알지 못한다` 신규. `allowEmptyShould(true)` 3곳 제거 |

### 실측한 것 (추측 아님)

1. **springdoc은 어드바이스의 `@ApiResponse`를 전역 적용한다 — 사실이다.**
   컨트롤러에 201만 적었는데 명세에 `["201","400","404","409"]`가 나왔다.
   덤으로 성공 스키마가 `{"type":"object"}` → `PlaceOrder`로 살아났다
2. **`OrderException`은 `@ExceptionHandler(Exception::class)`에 안 잡힌다.**
   스프링이 더 구체적인 핸들러를 고른다. 실패 경로 6종 전부 의도대로 나갔다
3. **롤백 테스트가 진짜로 롤백을 검사한다.**
   `@Transactional(noRollbackFor = [OrderException::class])`를 임시로 붙이니
   재고 부족 케이스만 빨간불이 됐다. 통과가 우연이 아니다

### 값 방식에서 잃은 것 / 되찾은 것

| 잃은 것 | 되찾았나 | 방법 |
|---|---|---|
| `when` exhaustive 검사 | 되찾음 | `OrderException`을 **sealed로 유지**. 어드바이스가 상위 하나만 잡고 안에서 `when` |
| 시그니처에 드러나는 실패 목록 | **못 되찾음** | Kotlin에 checked exception이 없다. `@throws` KDoc은 강제되지 않는다 |
| `data class`의 `equals` | 포기 | 테스트가 `assertFailsWith<T>` + 필드 단언으로 길어졌다 |
| 스택트레이스 비용 없음 | 되찾음 | `writableStackTrace=false`. 재고 부족은 초당 수백 번 나는 정상 결과다 |

### "일어나면 버그"와 "일상적 실패"를 가르는 선

값 방식에서는 문법에 있었다(`throw` vs `failure`). 지금은 **타입 계층이 그 자리다.**

- `OrderException` 상속 → 예상된 실패 → 4xx
- 상속 안 함 → 사고 → 500 (`Inventory.addReserved`의 음수 검사가 그 예)

**이 선이 무너지면 계측기가 고장난다.** 실수로 `IllegalArgumentException`을 `OrderException`
아래로 옮기면 서버 버그가 4xx로 위장되고 5xx 그래프가 평평해진다.

---

### 다음에 할 일 (2026-08-24 기준)

M1 1단계는 **끝났다.** 구현 · 오버셀 관측 · k6 측정 전부 완료.
지금 서 있는 자리는 M1 §13-7 — **"여기서 멈추고 리뷰 요청"** 이다.

셋 중에 아무거나 먼저 해도 된다. 서로 막지 않는다.

---

#### A. 전환된 코드 읽고 다시 짜보기 — 우선순위 높음

`be79e97`이 `DomainResult` → 예외 전환인데, **원래 네 몫이던 `backend/modules/**`까지
Claude가 대신 짰다.** 그걸 토대로 직접 다시 해보라고 한 커밋에 몰아놨다.

```bash
git show be79e97 --stat                  # 범위 파악
git show be79e97 -- backend/modules/     # 네 몫이었던 6파일만
git revert be79e97                       # 되돌리고 처음부터 하고 싶으면
```

읽을 때 볼 것 세 가지:
1. `OrderException`이 왜 **sealed**인가 — `catch`는 exhaustive가 아니다
2. `RuntimeException(msg, null, false, false)`의 마지막 `false`가 뭔가
3. `OrderPlacementService`가 **주문을 먼저 저장하고 재고를 나중에 검사**하는 이유

#### B. ADR 읽고 고치기 — Claude가 다 썼다

[`docs/adr/`](./adr/) · [쓰는 법](./adr/README.md)

2026-08-24부터 **ADR은 Claude가 쓴다.** 5건 다 작성돼 있다.
사용자 몫은 **읽고 어색한 부분을 고치는 것**이다 — 그것만으로도 원래 목적의 절반은 남는다.

| 문서 | 내용 |
|---|---|
| [0001 모듈러 모놀리스](./adr/0001-modular-monolith.md) | 왜 처음부터 나누지 않았나 |
| [0002 Flyway](./adr/0002-schema-migration-tool.md) | `ddl-auto: update`를 왜 안 쓰나 |
| [0004 선점 만료](./adr/0004-reservation-expiry-driver.md) | Redis TTL 이벤트를 왜 버렸나 |
| [0005 도메인 실패 표현](./adr/0005-domain-failure-representation.md) | **값으로 갔다 예외로 뒤집은 과정.** 재료가 제일 많다 |
| [0006 도메인·영속성 분리](./adr/0006-domain-persistence-separation.md) | 매핑 비용을 내고 무엇을 얻었나 |

0003은 2단계 락 수치가 나와야 쓴다. 0007(파생값 정합성 — 대사 vs 원장)은 후보로 잡아뒀다.

읽을 때 볼 것: **대안과 결과 절.** 결정만 적힌 ADR은 코드를 읽으면 알 수 있는 걸
반복하는 것이라 값어치가 없다. 그 두 절이 부실하면 고쳐달라고 말할 것.

#### C. §13-1의 "2단계 전에 정리할 빚" — **6건 완료, 1건은 2단계로 (2026-08-25)**

| 빚 | 처리 |
|---|---|
| `Clock` 주입 | `bootstrap/config/ClockConfig.kt` 신설, 서비스가 `Instant.now(clock)` |
| `Product.active` | 도메인에 필드 추가 + `Product.lineFor()`가 강제. 새 예외 `ProductInactive` → 409 |
| `Product.id` 기본값 | `UUID.randomUUID()` 제거 |
| `merge()` SELECT | `OrderEntity`가 `Persistable<String>` 구현 |
| `OrderLineRepository` ID 타입 | `String` → `Long` |
| `findByProductId`의 예외 | 포트가 `Inventory?`를 반환. 실패 판정은 application이 |
| `InventoryEntity.version`에 `@Version` | **안 함.** 낙관적 락이냐 조건부 UPDATE냐가 ADR-0003 주제다. 2단계에서 결정 |

덤으로 하나 더 찾았다 — `findByProductId`는 **파생 쿼리라 영속성 컨텍스트를 건너뛴다.**
조회와 갱신이 각각 파생 쿼리를 쓰는 바람에 `select inventories`가 두 번 나갔다.
`findById`로 바꾸니 1차 캐시를 타서 한 번이 된다.

**주문 1건이 내는 SQL: 7개 → 5개**

```
이전:  select products · select orders(merge) · insert orders · insert order_lines
       · select inventories · select inventories · update inventories
지금:  select products · select inventories · insert orders · insert order_lines
       · update inventories
```

**그런데 TPS는 안 움직였다.** 아래 참고.

#### D. ~~Claude에게 리뷰 요청~~ — 완료 (2026-08-25). **여기서 나온 4건이 다음 작업이다**

1단계 코드 + `be79e97` 전환분 리뷰를 받았다. 아래는 **아직 안 고친 것**이다.

**1. ~~`OrderRollbackIntegrationTest`의 단언이 실패할 수 없다~~ — 고침 (2026-08-25)**

`주문건수()`가 `product_id = 'p-rollback'` 라인을 가진 주문만 세고 있었다.
`p-없는상품`을 주문하는 테스트에서는 버그로 주문이 저장돼도 0이 나온다 —
**구조적으로 실패할 수 없는 단언이었다.**

고친 방법: **필터를 없애고 테이블 전체를 센다.** 대신 `@BeforeEach`가 orders를 비워
"0이어야 한다"가 성립하게 만든다. 범위를 좁히는 대신 시작점을 고정했다.

테스트 2건도 새로 넣었다.
- `여러 상품 중 하나만 재고가 모자라면 앞서 잡은 재고도 되돌아간다` — **검증 순서에
  의존하지 않는 롤백 테스트.** 재고 예약은 상품 단위로 순차 처리되므로 서비스가
  검증을 앞으로 당겨도 "일부는 쓰고 실패"가 반드시 생긴다 (아래 2번과 이어진다)
- `여러 상품 주문이 전부 성공하면 둘 다 잡힌다` — 다중 라인 대조군

`noRollbackFor`를 임시로 붙여 두 롤백 테스트가 **둘 다** 빨간불이 되는 것을 확인했다.

**2. `OrderPlacementService`가 주문을 먼저 저장하고 재고를 나중에 검사한다** ← 남음 (사용자 몫)

**실측으로 확인했다 (2026-08-25).** `logging.level.org.hibernate.SQL=DEBUG`로 켜고
409로 끝나는 요청 하나가 내는 SQL:

```
1  select  products        ← findByIds
2  select  orders          ← merge(). 직접 채운 @Id 때문에 INSERT 앞에 붙는 SELECT (§13-1의 빚)
3  insert  orders          ← 여기서 이미 DB에 갔다
4  insert  order_lines
5  select  inventories     ← 이 쿼리가 위 두 INSERT를 flush시켰다
```

**INSERT가 영속성 컨텍스트에만 머무는 게 아니라 실제로 DB에 도달한다.**
JPA가 쿼리 전에 auto-flush하기 때문이다(`FlushMode.AUTO`) — 5번 조회가 3·4번을 밀어낸다.
그리고 전부 롤백된다. 재고 소진 후에는 **모든 요청**이 이 경로다.

**고쳤다 (2026-08-25, 사용자 지시로 Claude가 `modules/`까지 작업).**
순서를 **읽기 → 검증 → 쓰기**로 되돌렸다. 재고가 모자라 실패하는 요청은
이제 `orders`/`order_lines`에 INSERT를 내지 않는다.

**되돌린 뒤 롤백 테스트가 여전히 살아 있는지 확인한 것이 핵심이다.**
`noRollbackFor`를 임시로 붙여 돌렸더니:

| 테스트 | 순서 되돌리기 전 | 되돌린 후 |
|---|---|---|
| 재고가 모자라면 409... | FAILED (롤백 검사함) | **PASSED** (쓸 게 없으니 검사 못 함) |
| 여러 상품 중 하나만... | FAILED | **FAILED** ← 여전히 검사한다 |

단일 상품 테스트는 롤백 검사 능력을 잃었다. **다중 상품 테스트가 그 역할을 넘겨받았다** —
재고 예약이 상품 단위로 순차 처리되므로 검증을 앞으로 당겨도 부분 쓰기는 없앨 수 없다.

**3. ~~`ProductController`가 `Unit`을 반환한다~~ — 구현 완료 (2026-08-25)**

빈 스텁을 지우고 실제 조회를 붙였다. 조회 포트를 새로 만들었다.

| 파일 | 내용 |
|---|---|
| `order-api/ProductCatalog.kt` | `ProductView` + `ProductCatalog` 포트. **읽기 모델은 도메인 `Product`와 다른 타입이다** |
| `order-core/infrastructure/ProductCatalogAdapter.kt` | `products ⋈ inventories` JPQL 조인. 행 타입은 infrastructure가 소유 |
| `bootstrap/web/order/ProductController.kt` | `GET /api/products` |

```json
[{"productId":"p-sneaker","name":"...","unitAmount":10000,
  "total":50,"reserved":7,"available":43}]
```

`available`은 필드가 아니라 **계산 프로퍼티**다. `total - reserved`는 언제나 참인 관계이고,
셋을 다 저장하면 어긋났을 때 무엇이 진실인지 정할 방법이 없다.
(ADR-0007 후보 "파생값 정합성"과 같은 질문이다)

`left join`이 아니라 `join`이다 — 재고 행이 없는 상품은 목록에서 빠진다.
`left join`으로 0을 채우면 "재고 없음"과 "재고 행 자체가 없음"이 같아진다. 후자는 데이터 사고다.

**k6 teardown이 살아났고, 살아나자마자 새 고장을 하나 더 찾았다 (아래 5번).**

**4. ~~명세 오염~~ — 고침 (2026-08-25). 범위를 좁혔다**

전역 어드바이스에 있던 `OrderException` 핸들러를
`bootstrap/web/order/OrderExceptionAdvice.kt`로 떼어내고
`@RestControllerAdvice(assignableTypes = [OrderController::class])`로 적용 대상을 좁혔다.

```
                     이전                         지금
POST /api/dev/reset  ['200','400','404','409']  → ['200']
GET  /api/health     ['200','400','404','409']  → ['200']
GET  /api/products   ['200','400','404','409']  → ['200']
POST /api/orders     ['201','400','404','409']  → 그대로
```

런타임 실패 경로(409/404/400)는 그대로다. 실측으로 확인했다.

**`@Order(HIGHEST_PRECEDENCE)`가 필요하다.** `ProblemDetailAdvice`의
`@ExceptionHandler(Exception::class)`도 `OrderException`에 매칭되는데,
스프링은 어드바이스 빈을 `@Order` 순서로 훑어 먼저 걸리는 것을 쓴다.
**클래스가 다르면 "더 구체적인 타입" 규칙이 적용되지 않는다** — 순서를 안 주면
재고 부족이 500으로 나갈 수 있다.

**5. k6 teardown이 오버셀 0건이라고 거짓 보고했다 — 고침 (2026-08-25)**

`GET /api/products`가 살아나자 teardown이 처음으로 실행됐고, 이렇게 찍혔다.

```
[최종] total=50 reserved=11 available=39
[오버셀] 0건                              ← 재고 50에 100건이 팔린 실행이다
```

`oversold = reserved - total`로 판정하고 있었다. **갱신 손실 때문에 `reserved`가
실제 판매량보다 훨씬 낮게 남으므로, 오버셀을 재려던 눈금이 다른 고장 때문에 망가져 있었다.**

고친 뒤:
```
[오버셀] 이 값으로는 판정할 수 없다.
         reserved(10) <= total(50)이지만, 갱신 손실이 있으면
         reserved 자체가 실제 판매량보다 낮게 남는다. order_succeeded와 재고를 비교할 것
[갱신 손실] reserved가 total보다 작다
```

**판정할 수 없을 때 "이상 없음"이라고 말하지 않는다.** 판정할 수 없다고 말한다.
`@ExceptionHandler(RuntimeException)`이 5xx를 4xx로 위장했던 것과 같은 종류의 사고다 —
계측기가 고장 난 채로 초록불이 켜진다.

**사소** — `OrderException.EmptyOrder`는 필드가 없는데 왜 `class`인가
(`data object`로 못 하는 이유는 있다) · `OrderQuery.findById`의 `@throws`가 정말 하나뿐인가

**리뷰에서 확인만 하고 넘어간 것:** `TODO()`가 던지는 `NotImplementedError`는 `Error`라
`@ExceptionHandler(Exception::class)`에 안 잡힐 줄 알았는데, 실측하니 잡힌다.
`DispatcherServlet`이 `Throwable`을 `ServletException`으로 감싸서 넘긴다. 문제 없다.

---

### SQL을 줄였는데 TPS가 안 올랐다 (2026-08-25)

주문 1건당 SQL을 7개에서 5개로 줄였다. **처리량은 그대로다.**

| | 정리 전 | 정리 후 (3회) |
|---|---|---|
| TPS | 845.5/s | 831 / 857 / 873 → 평균 ~854 |
| p95 | 143.67ms | 148.5 / 144.0 / 139.8 |
| p99 | 164.39ms | 177.7 / 163.7 / 158.1 |

노이즈 범위 안이다. **왕복 횟수가 병목이 아니었다.**
VU 100개가 전부 같은 상품 한 행을 UPDATE한다 — 그 행의 쓰기 락에 줄을 서는 것이
지배적이고, SELECT 두 번은 그 옆에서 묻힌다.

가치가 없었다는 뜻은 아니다. 2단계에서 낙관적 락을 켜면 **재시도가 트랜잭션을 통째로
다시 돌린다.** 그때는 트랜잭션 안의 왕복 하나하나가 재시도 횟수만큼 곱해진다.
지금 줄여둔 것이 그때 값을 한다. 다만 **지금 당장의 이득으로 계산하면 안 된다.**

> 예상하고 재봤더니 아니었다. 이런 것도 기록해야 한다 —
> "SQL을 줄였으니 빨라졌겠지"를 재보지 않고 넘어가면 그게 다음 사람의 오해가 된다.

#### ⚠ k6의 `iterations ... /s`를 TPS로 쓰면 안 된다

그 값의 분모는 **setup + 시나리오 + teardown 전체 시간**이다.
`GET /api/products`가 살아나면서 teardown이 실제로 돌기 시작했고,
setup의 dev/reset도 이전 실행에서 쌓인 주문 2만여 건을 지우느라 길어졌다.

```
총 실행 48.2초 (시나리오는 30초)
요약 표시   542/s        ← 틀렸다
실제        26,127 / 30s = 871/s
```

처음엔 이 값을 그대로 읽고 "TPS가 845 → 825로 떨어졌다"고 잘못 봤다.
**TPS = `iterations` 개수 ÷ 시나리오 duration**으로 직접 계산할 것.
k6 스크립트에도 경고를 달아뒀다.

### 그다음 (2단계)

`@Version` 낙관적 락 + 재시도 → M1 §8 표 2행.
**비교 기준선은 이미 있다:**

| | 1단계 (락 없음) |
|---|---|
| TPS | 845.6/s |
| p95 / p99 | 143.67ms / 164.39ms |
| 오버셀 | 50 (재고 50 / 100요청) |
| DB reserved | 8~21 ← 정상이면 100 |

2단계에서 볼 것:
- 오버셀 0 · `reserved == 성공 주문 수` — **합격 조건**
- **TPS가 얼마나 떨어지나** — 그게 낙관적 락의 가격이다
- **p99가 p95보다 벌어지나** — 재시도한 요청만 길어지므로 벌어져야 정상이다.
  1단계는 143 / 164로 거의 안 벌어져 있다 (재시도가 없으니 당연)

### 환경 재기동 순서

```bash
open -a OrbStack                                        # Docker 꺼져 있으면
docker compose -f infra/docker-compose.yml up -d        # Redis 6379 충돌은 무시해도 된다
cd backend && ./gradlew build                           # ConcurrentOrder 1건 FAILED가 정상
cd backend && ./gradlew :bootstrap:bootRun --args='--spring.profiles.active=dev'
```

k6 재측정이 필요하면:
```bash
docker run --rm -i --network host grafana/k6 run - < infra/k6/order-concurrent.js   # burst
docker run --rm -i --network host grafana/k6 run \
  --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)" \
  -e SCENARIO=throughput - < infra/k6/order-concurrent.js                            # 처리량
```
**p99는 `--summary-trend-stats`를 붙여야 나온다.** k6 기본 요약에는 없다.

### 미해결 지적 (리뷰에서 나온 것)

전부 M1 §13-1에 있다. 1단계 관측을 막지는 않지만 2단계 전에 정리해야 한다.

| 심각도 | 위치 | 내용 |
|---|---|---|
| 중요 | `InventoryEntity.version` | `@Version`이 없다. **2단계로 미룸** — 낙관적 락이냐 조건부 UPDATE냐가 ADR-0003 주제다. `OrderRepositoryAdapter`가 `version = 1`을 하드코딩하는 것도 같이 본다 |

나머지는 2026-08-25에 전부 처리했다. 위 "C" 표 참고.

### Claude가 할 일

- k6 수치 해석, 2단계 진입 판단
- ~~ArchUnit `allowEmptyShould(true)` 제거~~ — 완료 (payment 규칙 1곳만 남았다. M2에서 지운다)
- 프론트엔드는 2단계 통과 후

### 확정된 설계 결정

- **마이그레이션**: Flyway. 이력 테이블은 `public` 스키마 (모듈 스키마에 두면 M4 분리 때 딸려 간다)
- **선점 만료**: 주기 배치 단독. TTL 3분 / 주기 5초 / 1회 500건. Redis는 M1에서 안 쓴다
- **`orders.id`**: `varchar(100)`, 애플리케이션이 생성
- **도메인 실패** (2026-08-24 개정): `sealed class OrderException : RuntimeException`.
  ~~`DomainResult<E, T>`~~ 는 삭제했다. **뒤집은 이유는 트랜잭션 롤백** (M1 §4-2)
- **예외 계층에 프레임워크를 상속시키지 않는다**: `ErrorResponseException`을 쓰면
  `order-api`가 스프링에 묶여 M4 분리가 죽는다. ArchUnit이 강제한다
- **`OrderException`을 sealed로 유지한다**: `catch`는 exhaustive가 아니다.
  상위 하나만 잡고 `when`으로 분기해야 매핑 누락 시 컴파일이 깨진다
- **스택트레이스는 끈다**: `writableStackTrace=false`. 예상된 실패는 초당 수백 번 난다
- **시간**: 애플리케이션이 소유. 도메인 함수가 `now: Instant`를 받고 `updatedAt`을 갱신한다
- **도메인 ≠ 엔티티**: 매핑 비용을 내고 분리한다. ArchUnit이 강제
- **취소 가능 상태**: CREATED, PAID만. SHIPPED/DELIVERED는 거부 (반품은 별도 프로세스)
- **모듈 경계와 FK**: 같은 모듈 안이면 FK 걸고 JOIN한다(`products`). 경계를 넘으면 값으로만
  들고 있는다(`orders.account_id` — `accounts`는 payment 스키마). M4 물리 분리를 위해서다
- **가격 스냅샷**: `order_lines.unit_amount`에 주문 시점 단가를 복사해 박는다.
  정규화를 깨는 대신 가격 이력 정확성을 얻는다
- **포트는 사실만 반환한다**: 저장 실패는 도메인 실패가 아니라 사고 → 예외
- **HTTP 실패 매핑**: `OrderException` → RFC 9457 `ProblemDetail`. 표는 `OrderProblems.kt`.
  **400과 409를 가르는 기준은 "다시 시도해서 달라질 여지가 있는가"다.**
  변환 지점은 `ProblemDetailAdvice` 한 군데. 컨트롤러는 예외를 잡지 않는다
- **ADR은 Claude가 쓴다** (2026-08-24 변경). 0001·0002·0004·0005·0006 작성 완료.
  사용자는 읽고 어색한 부분을 고친다. 0003은 2단계 수치 대기, 0007(파생값 정합성)은 후보

### 환경 메모

- Gradle 9.3.0 / Kotlin 2.2.21 / Java 21 (`JAVA_HOME=~/.sdkman/candidates/java/current`)
- 인프라: `docker compose -f infra/docker-compose.yml up -d` (Docker가 꺼져 있으면 `open -a OrbStack`)
- Redis 6379가 이미 점유돼 있으면 compose가 실패하는데, **M1에서는 Redis를 안 쓴다.** 무시해도 된다
- Testcontainers가 Docker를 못 찾으면 테스트가 "환경 문제"로 실패한다. 코드 문제와 헷갈리지 말 것
- 마이그레이션을 고쳤으면 `down -v` 후 재기동. `public.flyway_schema_history`가 남으면 체크섬이 어긋난다
- DB에 psql로 직접 DDL을 넣지 말 것. Flyway 이력과 어긋나 `relation already exists`가 난다
- Kotlin 프로젝트에서 `@RequestBody`가 400이면 `jackson-module-kotlin`부터 의심할 것
- 통합 테스트가 컨테이너를 클래스마다 하나씩 띄운다. 지금 3개 클래스 = 3개 컨테이너
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
- [x] ADR 5건 (Claude, 2026-08-24) — [0001](./adr/0001-modular-monolith.md) ·
      [0002](./adr/0002-schema-migration-tool.md) · [0004](./adr/0004-reservation-expiry-driver.md) ·
      [0005](./adr/0005-domain-failure-representation.md) · [0006](./adr/0006-domain-persistence-separation.md)
- [ ] ADR-0003 재고 동시성 제어 — 2단계 수치가 나와야 쓴다
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
| 갱신 손실 (lost update) | M1 | `SET x = 읽은값 + 1`은 그 사이 남이 쓴 값을 덮어쓴다. 오버셀보다 고약하다 — 판 사실 자체가 장부에서 사라진다. **오버셀은 재고 한도가 상한이라 결정론적인데, 갱신 손실은 상한이 없어 경합만큼 커진다** (실측 88.8%) |
| 초록불인 고장 | M1 | 5xx 0건 · 실패율 0% · p99 164ms인데 장부의 88%가 증발해 있었다. **응답만 보는 계측은 데이터 정합성 고장을 못 잡는다** |
| 200을 반환하는 빈 스텁 | M1 | `Unit`을 반환하는 컨트롤러는 200 + 빈 본문이다. 호출자의 `status !== 200` 가드를 통과시켜 오류 처리를 무력화한다. 404가 차라리 낫다 |
| 실패를 값으로 vs 예외로 | M1 | 둘 다 해봤고 예외로 뒤집었다. **스프링은 예외만 보고 롤백한다** — 값으로 돌려주면 실패해도 커밋되므로 "쓰기 전에 다 검증한다"는 규율을 사람이 져야 한다 |
| sealed를 예외에 붙이는 이유 | M1 | `catch`는 케이스를 빠뜨려도 컴파일러가 침묵한다. 상위 하나만 잡고 안에서 `when`으로 분기해야 exhaustive 검사가 살아난다 |
| 예상된 실패의 스택트레이스 | M1 | `fillInStackTrace()`는 스택 깊이에 비례한다. 초당 수백 번 나는 정상 결과에 낼 비용이 아니다. `writableStackTrace=false`로 끈다 |
| RFC 9457 Problem Details | M1 | 에러 응답의 표준 형식. `type`은 분류(기계용), `detail`은 이번 건(사람용). 클라이언트가 `detail`을 파싱하면 설계 실패 |
| 400 vs 409 | M1 | "다시 시도해서 달라질 여지가 있는가". 400은 요청 자체가 틀린 것, 409는 지금 자원 상태와 충돌한 것 |
| 예외 핸들러의 범위 | M1 | 넓게 잡으면 계측기가 고장난다. `RuntimeException`을 400으로 바꾸자 5xx 그래프가 평평해지고 테스트가 영원히 통과했다 |

_나머지는 마일스톤을 마칠 때마다 사용자가 채운다._
