# M1 — 주문 코어와 동시성 제어

- 작성: Claude / 구현: 사용자
- 선행: M0 스캐폴딩 완료
- 상태: 구현 대기
- 관련 스펙: `docs/superpowers/specs/2026-08-18-commerce-lab-design.md` §4.2 ~ §4.4

---

## 0. 이 마일스톤이 노리는 것

"재고가 음수가 되지 않게 만들기"가 목적이 아니다. **음수가 되는 것을 직접 관측하고,
그 원인을 락·격리수준·경합의 언어로 설명할 수 있게 되는 것**이 목적이다.

그래서 3단계로 나눈다. 3단계 코드를 먼저 쓰면 1·2단계에서 배울 것이 사라진다.

| 단계 | 구현 | 관측할 것 |
|---|---|---|
| 1 | 락 없이 재고 차감 | 동시 100요청에서 오버셀 발생 (재고 음수) |
| 2 | 낙관적 락 `@Version` + 재시도 | 충돌 예외 급증, 재시도로 인한 처리량 저하 |
| 3 | 선점(HELD) + TTL + 조건부 UPDATE | 만료/확정 경쟁 조건, 다중 상품 데드락 |

각 단계 끝에서 **k6 수치를 표에 기록한다.** 그 표가 M1의 실제 산출물이다.
"락을 걸었더니 안전해졌다"가 아니라 "TPS가 A에서 B로 떨어졌고 오버셀은 N건에서 0건이 됐다"를
말할 수 있어야 한다.

---

## 1. 완료 기준

- [ ] k6 동시 100요청(재고 50)에서 **오버셀 0건**
- [ ] 3단계 처리량/지연/실패율 비교표 작성 (§8)
- [ ] 만료와 확정이 동시에 일어나는 경쟁 조건 테스트 통과
- [ ] 다중 상품 주문에서 데드락이 재현되지 않음 (또는 재현 후 락 순서로 해결한 기록)
- [ ] ADR 2건 이상 (사용자 작성 — §11 후보)
- [ ] 프론트에서 재고와 선점 상태가 실시간으로 보임 (Claude 몫)

---

## 2. 시작 전에 정해야 하는 것 — 스키마 마이그레이션 도구

M0는 스키마(`order`, `payment`) 두 개만 만들었다. 테이블은 아직 없다.
M1은 테이블을 5개 만든다. **어떻게 만들 것인가를 먼저 정해야 한다.**

| 방식 | 장점 | 버리는 것 |
|---|---|---|
| (가) `ddl-auto: update` | 코드만 쓰면 테이블이 생김. 빠름 | 실제 실행 SQL을 아무도 모름. 컬럼 삭제·인덱스·제약을 표현 못 함. 운영에서 쓸 수 없는 습관 |
| (나) **Flyway** | 버전별 SQL이 레포에 남음. 테스트/로컬/CI가 같은 스키마 | 마이그레이션 파일을 직접 써야 함. 되돌리기는 새 파일로만 |
| (다) Liquibase | XML/YAML 추상화, 롤백 지원 | 추상화 계층이 하나 더. Postgres 전용 기능 쓸 때 오히려 번거로움 |

**Claude 권고: (나) Flyway.**
근거는 M1의 학습 목표 자체가 SQL 수준의 락이라서다. 인덱스, `CHECK` 제약, 조건부 UPDATE를
직접 쓸 줄 알아야 하는데 (가)는 그 SQL을 프레임워크 뒤에 숨긴다.
또 3단계에서 재고 테이블에 인덱스를 붙였다 뗐다 하며 실행계획을 비교할 텐데,
(가)는 그 변경 이력을 남기지 못한다.

포기하는 것: 초기 속도. 엔티티 하나 추가할 때마다 SQL 파일을 같이 써야 한다.

> **결정됨 (2026-08-19): Flyway 채택.** ADR-0002로 사용자가 기록한다.
> Claude가 Gradle 의존성·`application.yml`·`bootstrap/src/main/resources/db/migration/` 디렉터리를 붙였다.
> 마이그레이션 SQL 자체는 스키마 설계이므로 사용자가 쓴다 (규칙은 그 디렉터리의 README 참고).
>
> 배선 시 내린 부수 결정 하나: **Flyway 이력 테이블은 `public` 스키마에 둔다.**
> 모듈 스키마(`"order"`/`payment`)에 넣으면 M4에서 DB를 쪼갤 때 그 이력이 한쪽 모듈에 딸려 간다.
> 이력 테이블은 도메인이 아니라 도구의 메타데이터이므로 CLAUDE.md §5의 "public에 도메인 테이블 금지"에 걸리지 않는다.

**면접에서 이렇게 말한다:** "ORM의 ddl-auto는 개발 편의 기능이지 스키마 관리 도구가 아닙니다.
운영 DB에 어떤 DDL이 나갔는지 레포에서 추적할 수 없으면 롤백도 감사도 불가능해서,
학습 프로젝트라도 처음부터 마이그레이션 도구를 썼습니다."

---

## 3. 데이터 모델

스키마는 `"order"` (예약어라 반드시 큰따옴표). `payment` 스키마는 M1에서 건드리지 않는다.

```
orders          id, account_id, status, total_amount, created_at, updated_at, version
order_lines     id, order_id, product_id, quantity, unit_amount
products        id, name, unit_amount              -- 카탈로그. M1 최소 형태
inventories     product_id(PK), total, reserved, version
reservations    id, order_id, product_id, quantity, status, expires_at, created_at
                status: HELD | CONFIRMED | RELEASED | EXPIRED
```

설계 포인트 세 가지. 각각 왜 그런지 스스로 답해보고 넘어갈 것.

1. `inventories`에 `available` 컬럼이 없다. `available = total - reserved`로 계산한다.
   — 같은 사실을 두 곳에 저장하지 않는다. 저장하면 둘이 어긋나는 순간이 반드시 온다.
2. `reservations.expires_at`이 nullable이 아니다. HELD가 아닌 상태에서도 값이 남는다.
   — 만료 시각은 "언제 만료되기로 했었나"라는 사실이고, 상태가 바뀌어도 사실은 안 바뀐다.
3. `orders`와 `inventories` 둘 다 `version`을 갖는다. 2단계에서 쓴다.
   — 1단계에서는 컬럼만 만들고 쓰지 않는다.

**제약 조건 하나는 반드시 넣는다:**

```sql
ALTER TABLE "order".inventories
    ADD CONSTRAINT inventories_reserved_not_exceeding_total
    CHECK (reserved >= 0 AND reserved <= total);
```

1단계에서 이 제약이 오버셀을 **막지 못하는 것이 아니라, 오버셀을 예외로 드러내 준다.**
제약이 없으면 재고가 조용히 음수가 되고, 있으면 DB가 소리를 지른다.
어느 쪽이 관측하기 좋은가는 §8에서 직접 판단한다.

---

## 4. 인터페이스 — 시그니처만

구현은 쓰지 않는다. 시그니처와 그 시그니처를 그렇게 정한 이유만 제시한다.

### 4.1 `order-api` — 외부에 노출되는 유일한 창구

```kotlin
package com.commercelab.order.api

import java.time.Instant

data class PlaceOrderCommand(
    val accountId: String,
    val lines: List<Line>,
) {
    data class Line(val productId: String, val quantity: Int)
}

data class PlacedOrder(
    val orderId: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val reservations: List<ReservationView>,
)

data class ReservationView(
    val reservationId: String,
    val productId: String,
    val quantity: Int,
    val status: ReservationStatus,
    val expiresAt: Instant,
)

enum class OrderStatus { CREATED, PAID, CANCELLED, SHIPPED, DELIVERED }
enum class ReservationStatus { HELD, CONFIRMED, RELEASED, EXPIRED }

sealed interface OrderError {
    data class OutOfStock(val productId: String, val requested: Int, val available: Int) : OrderError
    data class ProductNotFound(val productId: String) : OrderError
    data class InvalidQuantity(val productId: String, val quantity: Int) : OrderError
    data class OrderNotFound(val orderId: String) : OrderError
    data class ConflictExhausted(val attempts: Int) : OrderError   // 2단계에서 쓴다
    data object ReservationAlreadySettled : OrderError             // 3단계에서 쓴다
}

interface OrderPlacement {
    fun place(command: PlaceOrderCommand): Result<PlacedOrder>
}

interface OrderQuery {
    fun findById(orderId: String): Result<PlacedOrder>
}
```

`Result<T>`를 쓰고 예외를 던지지 않는 이유는 스펙 §6에 있다. 재고 부족은 사고가 아니라
정상적인 비즈니스 결과다. 예외로 만들면 호출자가 catch를 잊었을 때 500이 나간다.

> `kotlin.Result`를 쓸지, 직접 만든 `Either<OrderError, T>`를 쓸지는 사용자가 정한다.
> `kotlin.Result`는 실패 타입이 `Throwable`로 고정된다는 제약이 있다. 이게 문제가 되는지
> 직접 부딪혀 보고 판단할 것. (2단계 재시도 로직을 짤 때 답이 나온다.)

### 4.2 3단계에서 추가되는 포트

```kotlin
package com.commercelab.order.api

import java.time.Instant

interface ReservationLifecycle {
    /** 결제 성공 시 선점을 확정한다. 이미 만료된 선점이면 실패해야 한다. */
    fun confirm(orderId: String): Result<Unit>

    /** 만료된 선점을 해제한다. 반환값은 이번 실행에서 실제로 해제한 건수. */
    fun releaseExpired(now: Instant, limit: Int): Int
}

interface InventoryQuery {
    fun snapshot(productIds: List<String>): List<InventorySnapshot>
}

data class InventorySnapshot(
    val productId: String,
    val total: Int,
    val reserved: Int,
) {
    val available: Int get() = total - reserved
}
```

`releaseExpired`가 `limit`을 받는 이유를 생각해볼 것. 만료 건이 10만 건 쌓인 상태에서
`UPDATE ... WHERE expires_at < now()`를 조건 없이 날리면 무슨 일이 생기나?

### 4.3 bootstrap REST 어댑터 (Claude가 프론트에서 소비)

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/api/products` | 상품 + 재고 스냅샷 |
| POST | `/api/orders` | 주문 생성 |
| GET | `/api/orders/{orderId}` | 주문 조회 |
| POST | `/api/orders/{orderId}/confirm` | **M1 한정** 결제 성공 흉내. M3에서 이벤트로 대체 |
| POST | `/api/dev/reset` | 부하 테스트용 재고 초기화. `dev` 프로필에서만 등록 |

`/api/dev/reset`을 프로필로 격리하는 이유: k6를 반복 실행하려면 초기화가 필요한데,
이런 엔드포인트가 운영 프로필에 남으면 그것 자체가 사고다.

**트랜잭션 경계는 `order-core`의 애플리케이션 서비스에만 둔다.**
bootstrap의 컨트롤러에 `@Transactional`을 붙이면 ArchUnit이 빌드를 깬다 (M0 규칙 4·5).

---

## 5. 실패하는 테스트 — 실행 가능한 스펙

Claude가 제공하는 스펙이다. 구현 전에는 전부 실패한다.
**단, `1단계`로 표시된 테스트는 1단계에서 실패하는 것이 정상이다.** 그 실패가 관측 결과다.

### 5.1 도메인 단위 테스트 (스프링 없음) — `order-core/src/test`

```kotlin
package com.commercelab.order

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderTest {

    @Test
    fun `주문 총액은 라인 금액의 합이다`() {
        // given: 1000원 2개 + 2500원 1개
        // then: 4500
    }

    @Test
    fun `수량이 0 이하인 라인은 주문을 만들 수 없다`() {
        // OrderError.InvalidQuantity
    }

    @Test
    fun `CREATED 주문만 PAID로 갈 수 있다`() {
        // CANCELLED -> PAID 는 실패해야 한다
    }

    @Test
    fun `CANCELLED 주문은 다시 CANCELLED로 갈 수 없다`() {
        // 멱등이 아니라 실패다. 왜 그렇게 정했는지 스스로 답할 것.
        // (반대로 멱등이어야 한다고 판단했다면 그 근거를 ADR에 남길 것)
    }
}
```

### 5.2 동시성 통합 테스트 — `bootstrap/src/test` (Testcontainers)

```kotlin
package com.commercelab.order

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrentOrderIntegrationTest {

    /**
     * 재고 50에 동시 100요청. 성공은 정확히 50건이어야 한다.
     *
     * [1단계] 이 테스트는 실패한다. 실패를 기록하고 넘어간다.
     *         실패 메시지에 찍힌 성공 건수가 곧 오버셀 수치다.
     * [2단계] 통과해야 한다.
     */
    @Test
    fun `동시 100요청에서 재고 50개를 초과 판매하지 않는다`() {
        val threads = 100
        val stock = 50
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val succeeded = AtomicInteger()

        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            pool.submit {
                ready.countDown()
                start.await()
                // placeOrder(productId, quantity = 1) 성공 시 succeeded.incrementAndGet()
            }
        }
        ready.await(10, TimeUnit.SECONDS)
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(60, TimeUnit.SECONDS)

        assertEquals(stock, succeeded.get(), "성공 건수가 재고를 넘으면 오버셀이다")
        // 그리고 DB에서 직접 확인한다 — 애플리케이션 카운터를 믿지 않는다
        // assertEquals(stock, selectReserved(productId))
    }

    /**
     * [2단계] 낙관적 락 충돌은 재시도로 흡수되어야 한다.
     * 재시도 한도를 넘긴 요청은 500이 아니라 명시적 에러(ConflictExhausted)로 끝나야 한다.
     */
    @Test
    fun `충돌이 재시도 한도를 넘으면 명시적 에러로 끝난다`() {
    }

    /**
     * [3단계] 만료와 확정의 경쟁 조건. 이 마일스톤의 핵심이다.
     *
     * 선점이 만료 직전인 상태에서 만료 처리와 확정 처리를 동시에 실행한다.
     * 둘 중 정확히 하나만 성공해야 한다. 둘 다 성공하면
     * "재고는 남에게 넘어갔는데 결제는 성공한" 상태가 된다.
     */
    @Test
    fun `만료 처리와 확정 처리는 서로를 배제한다`() {
        // given: expires_at = now + 50ms 인 HELD 예약
        // when : releaseExpired(now+100ms) 와 confirm(orderId) 를 동시에 실행
        // then : 정확히 하나만 성공. reservations.status 는 EXPIRED 또는 CONFIRMED 중 하나
        //        재고(reserved)는 그 결과와 일관돼야 한다
    }

    /**
     * [3단계] 다중 상품 주문의 락 순서.
     *
     * A는 [상품1, 상품2] 순으로, B는 [상품2, 상품1] 순으로 동시에 주문한다.
     * 데드락이 나면 이 테스트는 타임아웃이나 DeadlockLoserDataAccessException으로 실패한다.
     */
    @Test
    fun `상품 순서가 엇갈린 동시 주문에서 데드락이 나지 않는다`() {
    }

    /**
     * [3단계] 만료된 선점의 재고는 다시 팔 수 있어야 한다.
     */
    @Test
    fun `만료된 선점은 재고를 반환한다`() {
    }
}
```

> 테스트 본문을 비워둔 것은 게으름이 아니다. **무엇을 검증해야 하는지는 스펙이고,
> 어떻게 준비하고 호출할지는 구현 설계다.** 후자를 대신 써주면 API 설계를 사용자가 하지 않게 된다.
> 시그니처가 확정되면 Claude가 본문을 채운 실제 테스트 파일을 커밋한다 (§10 진행 순서 참고).

### 5.3 아키텍처 테스트에 추가될 규칙

M1 구현이 시작되면 Claude가 다음 규칙을 `ArchitectureTest`에 추가한다.

- `order-core`의 도메인 패키지는 `jakarta.persistence`를 알지 못한다 (엔티티와 도메인 모델 분리를 택한 경우)
- `@Transactional`은 `order-core`의 애플리케이션 계층에만 존재한다

두 번째 규칙은 "트랜잭션을 어디서 여는가"를 강제한다. 리포지터리마다 트랜잭션을 열면
주문 생성이 원자적이지 않게 된다.

---

## 6. 단계별 작업 순서

### 1단계 — 락 없이 (목표: 깨뜨리기)

1. Flyway 마이그레이션 작성 (테이블 6개 + CHECK 제약)
2. 도메인 모델 + 단위 테스트 통과 (§5.1)
3. `OrderPlacement` 구현 — 재고 조회 후 검사, 그 다음 UPDATE. **락 없음**
4. REST 어댑터 + `/api/dev/reset`
5. `ConcurrentOrderIntegrationTest` 실행 → **실패 확인**
6. k6 100 VU 실행 → 오버셀 건수 기록 (§8 표)

여기서 멈추고 관측 결과를 §8 표에 적는다. 바로 2단계로 넘어가지 않는다.

**확인 질문:** 재고 조회와 UPDATE 사이에 다른 트랜잭션이 끼어들 수 있는 이유는 무엇인가?
격리 수준을 `REPEATABLE READ`로 올리면 해결되는가? 해보고 결과를 기록할 것.

### 2단계 — 낙관적 락

1. `inventories.version`에 `@Version` 적용
2. 충돌 시 재시도: 최대 횟수, 백오프 유무를 직접 정한다
3. 재시도 한도 초과 → `OrderError.ConflictExhausted`
4. 테스트 통과 확인 (오버셀 0)
5. k6 재실행 → TPS·p99·충돌율 기록

**확인 질문:** 재시도를 트랜잭션 안에서 하는가 밖에서 하는가? 안에서 하면 무엇이 깨지는가?

**비교 실험(권장):** 같은 시나리오를 `SELECT ... FOR UPDATE`(비관적)로도 구현해 수치를 재본다.
경합이 심할 때 어느 쪽이 빠른지 예측하고, 실제 결과와 비교해 어긋나면 왜인지 설명할 것.
이 비교표가 ADR의 근거가 된다.

### 3단계 — 선점 + TTL

1. `reservations` 도입. 주문 생성은 HELD 선점을 만든다
2. `confirm(orderId)` — HELD → CONFIRMED, 조건부 UPDATE
3. `releaseExpired(now, limit)` — 만료된 HELD → EXPIRED, `inventories.reserved` 감소
4. 스케줄러 또는 §11에서 합의한 방식으로 만료 구동
5. 경쟁 조건 테스트, 데드락 테스트 통과
6. k6 재실행 → 최종 수치 기록

**핵심 힌트는 §9에 접어뒀다. 최소 30분은 직접 붙어본 뒤 열 것.**

---

## 7. 격리 수준과 커넥션 풀 — 같이 관측할 것

M1은 락만 배우는 마일스톤이 아니다. 다음 두 가지를 부하 중에 함께 본다.

**격리 수준.** Postgres 기본은 `READ COMMITTED`다. 1단계 오버셀은 격리 수준을 올리면
사라지는가? `REPEATABLE READ`에서는 무엇이 대신 나타나는가(힌트: 직렬화 실패)?
`SERIALIZABLE`은 왜 기본값이 아닌가?

**커넥션 풀.** HikariCP 기본 풀 크기는 10이다. k6 VU를 100으로 올리면
요청 100개가 커넥션 10개를 두고 줄을 선다. 이때 관측할 것:

- `hikaricp_connections_pending` (Prometheus에 이미 노출돼 있다)
- p99 지연이 어디서 급증하는가 — DB 락인가 커넥션 대기인가

풀을 100으로 키우면 빨라지는가? 직접 해보고 결과를 기록할 것.
(Postgres `max_connections` 기본값이 100이라는 사실과 함께 생각해볼 것.)

**Grafana:** http://localhost:3001 → 대시보드 **M1 — 주문 동시성** (프로비저닝 완료).
패널 6개: 주문 TPS(상태코드별) / p95·p99 지연 / HikariCP 커넥션 / 커넥션 획득 대기 /
응답 상태 비율 / 도메인 카운터.

마지막 패널은 아직 비어 있다. Micrometer `Counter`를 직접 심으면 채워진다 —
`orders.placed`, `orders.conflict`, `reservations.expired`. 이 세 개를 심을지 말지는 구현자가 정한다.
(심지 않으면 충돌율을 로그에서 세야 한다.)

---

## 8. 측정 표 (사용자가 채운다)

k6 시나리오는 `infra/k6/order-concurrent.js`에 있다. 두 가지를 따로 잰다.

```bash
# 오버셀 관측 — 재고 50에 100요청 동시 투입
docker run --rm -i --network host grafana/k6 run - < infra/k6/order-concurrent.js

# 처리량 측정 — 재고를 크게 잡고 30초 지속 부하
docker run --rm -i --network host grafana/k6 run -e SCENARIO=throughput - < infra/k6/order-concurrent.js
```

나눈 이유: 오버셀은 "재고보다 요청이 많은" 상황이 필요하고 처리량은 "재고 부족으로 인한
조기 실패가 없는" 상황이 필요하다. 한 번에 재면 후반부가 전부 품절 응답이 되어 TPS가 왜곡된다.

시나리오는 `POST /api/dev/reset`, `POST /api/orders`, `GET /api/products`를 호출한다.
§4.3의 계약과 다르게 구현했다면 이 파일도 같이 고쳐야 한다.

| 단계 | 성공 주문 | 오버셀 | TPS | p95 | p99 | 실패율 | 재시도/충돌 |
|---|---|---|---|---|---|---|---|
| 1 — 락 없음 | | | | | | | — |
| 2 — 낙관적 락 | | 0 | | | | | |
| 2b — 비관적 락(선택) | | 0 | | | | | — |
| 3 — 선점 + TTL | | 0 | | | | | |

이 표가 채워지면 면접에서 이렇게 말할 수 있다:
"락 없이는 100요청 중 N건이 오버셀됐고, 낙관적 락으로 0건이 됐지만 TPS가 X% 떨어졌습니다.
경합이 심한 구간에서는 비관적 락이 오히려 빨랐는데, 재시도 자체가 비용이기 때문입니다."

---

## 9. 힌트 (막혔을 때만)

<details>
<summary>1단계 — 오버셀이 재현되지 않는다면</summary>

재현이 안 되는 흔한 이유 세 가지:

1. 스레드가 실제로 동시에 출발하지 않았다 → `CountDownLatch`로 출발선을 맞췄는지 확인
2. 트랜잭션이 너무 짧아 겹칠 틈이 없다 → 조회와 UPDATE 사이에 의도적으로 `Thread.sleep(10)`을 넣어
   창을 벌려본다. (관측용이며 커밋하지 않는다)
3. JPA 1차 캐시가 조회를 가로챘다 → 같은 트랜잭션에서 반복 조회 중인지 확인

</details>

<details>
<summary>2단계 — 재시도를 어디에 둘 것인가</summary>

`@Transactional` 메서드 **안에서** 재시도하면, 첫 시도에서 발생한 예외로 이미
트랜잭션이 rollback-only로 표시돼 있다. 재시도해도 커밋 시점에 터진다.

재시도는 트랜잭션 경계 **바깥**에 있어야 한다. 즉 재시도를 담당하는 층과
트랜잭션을 여는 층이 분리돼야 한다. 스프링의 `@Retryable`을 쓰든 직접 루프를 돌든
이 구조는 같다.

</details>

<details>
<summary>3단계 — 만료와 확정을 배제시키는 방법</summary>

애플리케이션에서 `if (조회한 상태 == HELD) { update }` 를 하면 조회와 UPDATE 사이가 벌어진다.
검사와 갱신을 **한 문장**으로 합친다.

```sql
UPDATE "order".reservations
   SET status = 'CONFIRMED'
 WHERE id = :id
   AND status = 'HELD'
   AND expires_at > :now
```

영향받은 행 수가 0이면 이미 만료됐거나 이미 확정된 것이다.
`updateCount == 0`을 실패로 해석하는 것이 핵심이다. 만료 쪽도 대칭으로 쓴다
(`status = 'HELD' AND expires_at <= :now`).

이 패턴 이름: compare-and-set. DB 한 문장이 원자적이라는 성질에 기댄다.

</details>

<details>
<summary>3단계 — 데드락</summary>

트랜잭션 A가 상품1 → 상품2 순으로 락을 잡고, B가 상품2 → 상품1 순으로 잡으면
서로가 서로를 기다린다. 해결책은 **모든 트랜잭션이 같은 순서로 락을 잡는 것**이다.

무엇을 기준으로 정렬할지는 결정 사항이다(product_id 사전순 등).
정렬은 어느 계층에서 해야 하는가 — 리포지터리인가, 도메인인가? 이건 스스로 답할 것.

</details>

---

## 10. 진행 순서 — 지금부터

1. **§2 마이그레이션 도구 결정** (사용자) → Flyway면 Claude가 Gradle·디렉터리 설정
2. **§11 트레이드오프 질문 답변** (사용자) → Claude 판단 → 합의 → ADR
3. Claude: Gradle 의존성(JPA, Flyway), k6 시나리오, Grafana 대시보드 배선
4. **사용자: `order-api` 시그니처 파일 작성** — §4는 제안이고, 확정은 사용자가 한다.
   `modules/**/src/main/**`은 사용자 영역이다 (CLAUDE.md §1). Claude가 대신 쓰지 않는다
5. Claude: 확정된 시그니처에 맞춰 §5 테스트 파일을 본문까지 채워 커밋 (`src/test`는 Claude 몫)
6. 사용자: 1단계 구현 → 관측 → 표 기록
7. Claude 리뷰 → 2단계 → 리뷰 → 3단계 → 리뷰
8. 프론트(Claude) — 2단계 통과 시점에 붙인다. API가 안정돼야 타입 생성이 의미 있다
9. 회고 `docs/retro/M1.md`, PROGRESS.md 갱신

---

## 11. 트레이드오프 질문 — 선점 만료 구동 방식 (합의 완료)

> **선점 만료를 무엇이 구동하는가?**

세 후보가 있고, 셋 다 실무에서 쓰인다.

| | 방식 | 성격 |
|---|---|---|
| (가) | 주기적 배치 — 스케줄러가 N초마다 만료된 HELD를 훑어 해제 | 만료가 "언젠가" 반영됨. 지연 상한 = 주기 |
| (나) | 조회 시점 lazy 만료 — 재고를 읽을 때 만료된 선점을 그 자리에서 정리 | 읽기 경로에 쓰기가 섞임. 아무도 안 보면 영원히 안 풀림 |
| (다) | Redis TTL 만료 이벤트(keyspace notification)로 즉시 트리거 | 반응이 빠름. 이벤트 유실 시 영원히 안 풀림 → DB 배치 병행 필요 |

생각해볼 지점:

- 재고 1개를 두고 100명이 대기 중이라면, 만료가 5초 늦게 반영되는 것의 비용은 얼마인가?
- (나)에서 "아무도 그 상품을 조회하지 않는" 상황은 실제로 문제인가? 재고가 잠긴 채 방치되면
  누가 손해를 보는가?
- 스펙 §5는 "진실의 원천은 항상 DB, Redis는 신호용 보조"라고 못 박았다. (다)를 택하면
  그 규칙을 어떻게 지키는가?
- 배치 주기를 1초로 줄이면 (다)와 실질적으로 무엇이 다른가?

### 합의 결과 (2026-08-19)

**(가) 주기 배치 단독. Redis는 M1에서 쓰지 않는다.**

| 파라미터 | 값 | 근거 |
|---|---|---|
| 선점 TTL | **3분** | 결제 완료에 넉넉한 시간. 사용자 판단 |
| 만료 배치 주기 | **5초** | 만료 지연이 TTL의 3% 미만. 1초로 줄여도 체감 개선은 미미하고 DB 부하만 는다 |
| 배치 1회 처리 한도 | 500건 | 한 트랜잭션이 잡는 락의 범위를 제한한다 |
| 최악의 재고 잠김 | 3분 5초 | TTL + 배치 주기 |

후보를 자른 논리:

- **(나) lazy 배제** — 조회는 조회만 한다는 원칙. 읽기 경로에 쓰기를 넣으면 read replica 분리가
  막히고, 인기 상품에서 조회가 락 핫스팟을 만든다. 조회 때문에 주문이 느려지는 구조는 비대칭적으로 나쁘다.
- **(다) Redis 단독 배제** — 구독자가 죽어 있는 동안 만료된 키의 알림은 증발한다.
  그 경우 해당 재고는 영영 잠긴다. "영영 잠김은 최소화해야 한다"는 요구와 충돌.
- **(다)를 나중에 붙여도 (가)는 못 없앤다.** 정합성 책임이 여전히 배치에 남으므로 (다)는 순수한
  지연 최적화다. 지금 넣을 이유가 없다 (YAGNI). 스펙 §5의 "진실의 원천은 DB, Redis는 신호용 보조"가
  바로 이 구조다.

**포기한 것:** 즉시성. 만료 시각과 재고 반환 사이에 최대 5초의 창이 남는다.
그 창 동안 "실제로는 재고가 있는데 품절로 보이는" 응답이 나갈 수 있다.

**쓰기 경로 lazy 회수는 M1 본류에서 제외한다.** (주문이 재고 부족으로 실패하기 직전에 해당 상품의
만료분을 즉시 회수하고 1회 재시도하는 방식.) 이유는 두 가지다.

1. **측정 오염** — 2단계 산출물이 "락 때문에 TPS가 얼마나 떨어졌나"인데, 실패 경로에 회수 UPDATE와
   재시도가 끼면 지연 원인을 락과 회수로 분리하기 어려워진다.
2. **경합 증폭** — 경합이 심한 순간에는 실패한 요청들이 동시에 같은 상품의 회수 UPDATE를 날린다.
   해결하려던 문제(락 경합)를 키운다.

대신 **3단계 완료 후 선택 실험**으로 켜서 수치를 비교한다. "잘못된 품절 응답은 사라졌지만 p99가
얼마 올랐다"는 데이터가 남으면 그것이 ADR-0004의 근거가 된다.

**면접에서 이렇게 말한다:** "선점 만료를 조회 시점에 처리하는 방식도 검토했지만, 읽기 경로에 쓰기를
넣으면 read replica 분리가 막히고 인기 상품에서 락 핫스팟이 생깁니다. Redis TTL 이벤트는 유실 시
재고가 영구히 잠기는 위험이 있어 단독으로는 쓸 수 없었고요. 만료는 지연 상한이 보장되는 배치로
빼고, TTL 3분에 5초 주기를 잡아 지연을 TTL의 3% 이내로 뒀습니다."

---

## 12. ADR 후보

- ADR-0002: 스키마 마이그레이션 도구 (§2)
- ADR-0003: 재고 동시성 제어 방식 — 낙관적 락 vs 비관적 락 (2단계 수치 근거)
- ADR-0004: 선점 만료 구동 방식 — 배치 단독, TTL 3분 / 주기 5초 (§11 합의 결과)
- ADR-0005: 도메인 에러 표현 — `kotlin.Result` vs 자체 `Either` (§4.1에서 부딪힌 결과)

---

## 13. 내가 할 일 (체크리스트)

각 항목은 "무엇을 하면 끝인지"를 명령과 출력으로 판정한다.
막히면 §9 힌트를 열기 전에 최소 30분은 붙어본다.

### 지금: 1단계 — 락 없이 만들고 깨뜨리기

- [ ] **1. `V1__create_order_tables.sql` 완성**
  - 위치: `backend/bootstrap/src/main/resources/db/migration/`
  - 테이블 5개 + `products`: §3의 컬럼 목록대로
  - `inventories`에 CHECK 제약 (§3 하단 SQL)
  - 주의: 스키마 명시(`"order".orders`), `order`는 예약어라 큰따옴표 필수
  - **완료 판정:**
    ```bash
    docker compose -f infra/docker-compose.yml up -d
    cd backend && ./gradlew :bootstrap:bootRun
    # 다른 터미널에서
    docker exec -it commerce-postgres psql -U commerce -d commerce -c '\dt "order".*'
    ```
    → 테이블 6개가 나오면 끝

- [ ] **2. `order-api` 시그니처 확정**
  - 위치: `backend/modules/order/order-api/src/main/kotlin/com/commercelab/order/api/`
  - §4.1이 제안이다. 그대로 써도 되고 바꿔도 된다. **결정은 내가 한다**
  - 여기서 정할 것 두 가지:
    - 실패를 `kotlin.Result`로 표현할까, 자체 `Either<OrderError, T>`로 할까 (§4.1 주석 참고)
    - `orderId`는 서버가 만드나, 클라이언트가 주나
  - **완료 판정:** `./gradlew :modules:order:order-api:build` 성공
  - 끝나면 나에게 알려줄 것 → §5 테스트 파일을 본문까지 채워 커밋한다

- [ ] **3. 도메인 모델 + 단위 테스트 통과**
  - 위치: `backend/modules/order/order-core/src/main/kotlin/`
  - **완료 판정:** `./gradlew :modules:order:order-core:test` — §5.1의 4건 통과

- [ ] **4. 락 없이 구현 + REST 어댑터**
  - 재고를 조회하고, 검사하고, UPDATE한다. **락을 걸지 않는다** (일부러)
  - bootstrap에 §4.3의 엔드포인트 5개. `/api/dev/reset`은 `dev` 프로필에만
  - 주의: `@Transactional`은 order-core에만. bootstrap에 붙이면 ArchUnit이 빌드를 깬다
  - **완료 판정:** `curl -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -d '{"accountId":"a1","lines":[{"productId":"p-sneaker","quantity":1}]}'` 가 주문을 만든다

- [ ] **5. 오버셀 관측하고 기록**
  ```bash
  ./gradlew :bootstrap:test --tests '*ConcurrentOrderIntegrationTest*'   # 실패해야 정상
  docker run --rm -i --network host grafana/k6 run - < infra/k6/order-concurrent.js
  ```
  - k6 출력 마지막 줄 `[오버셀] N건`을 §8 표 1행에 적는다
  - Grafana(http://localhost:3001 → M1 대시보드)에서 p99와 커넥션 대기도 같이 본다
  - **완료 판정:** §8 표의 "1 — 락 없음" 행이 채워짐

- [ ] **6. 여기서 멈추고 리뷰 요청**
  - 2단계로 바로 넘어가지 않는다. 관측 결과를 놓고 이야기한 뒤 넘어간다

### 그 다음 (지금 안 해도 됨)

- [ ] 2단계 — `@Version` 낙관적 락 + 재시도 → §8 표 2행. 비관적 락 비교는 선택
- [ ] 3단계 — 선점 HELD + TTL 3분 + 만료 배치 5초 → §8 표 4행
- [ ] 프론트 붙이기는 내 몫이다. 2단계 통과하면 알려줄 것

### 병행해서 언제든

- [ ] **ADR-0002 작성** — 마이그레이션 도구. 합의 끝났고 기록만 남았다 (§2)
- [ ] **ADR-0004 작성** — 선점 만료 구동 방식. 합의 끝남 (§11)
  - `docs/adr/0001-modular-monolith.md`가 형식 예시다
  - 내 설명을 그대로 옮기지 말고 내 언어로 다시 쓸 것. 그게 면접 답변의 원본이 된다

### 막혔을 때

- 30분 넘게 안 풀리면 §9 힌트를 연다
- 그래도 안 되면 "여기서 막혔다"고 말해줄 것. 답을 주기 전에 질문부터 하겠지만,
  두 번 물어도 안 풀리면 설명한다
