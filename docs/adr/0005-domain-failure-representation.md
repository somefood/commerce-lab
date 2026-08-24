# ADR-0005: 도메인 실패를 예외로 표현한다 (값으로 갔다가 뒤집었다)

## 상태

채택 (2026-08-24) — 하루 전(8/23)의 "값으로 표현한다" 결정을 번복한 것이다

번복을 별도 ADR로 쪼개지 않는다. 8/23 결정은 문서로 남기기 전이라 supersede할 대상이 없고,
**무엇보다 뒤집은 과정 자체가 이 문서의 내용이다.** 두 개로 나누면 그게 끊긴다.

## 배경

재고가 부족할 때 호출자에게 어떻게 알릴 것인가.

문제는 실패가 **두 종류**라는 것이었다.

- **재고 부족** — 인기 상품이면 하루에 수천 번 일어난다. 예상된 정상적인 결과다
- **음수 수량이 도메인까지 내려옴** — 위쪽 검증이 뚫렸다는 뜻이다. 버그다

이 둘을 같은 방식으로 다루면 구분이 사라진다. 그리고 그 구분이 사라지면
HTTP 응답에서 4xx와 5xx를 가를 근거가 없어진다.
서버 버그가 4xx로 위장되면 알림이 안 울리고, 5xx 그래프는 평평해진다.

## 결정

**예상된 비즈니스 실패는 `sealed class OrderException : RuntimeException`으로 던진다.**

```kotlin
sealed class OrderException(message: String) : RuntimeException(message, null, false, false) {
    class OutOfStock(val productId: String, val requested: Int, val available: Int) : OrderException(...)
    class ProductNotFound(val productId: String) : OrderException(...)
    // ...
}
```

함께 정한 것 넷.

**1. 계층을 `sealed`로 유지한다**

`catch`는 `when`과 달리 케이스를 빠뜨려도 컴파일러가 잡아주지 않는다.
그래서 어드바이스에서 `OrderException` 하나로 받고 **안에서 `when`으로 분기한다.**
하위 클래스를 추가하고 매핑을 빠뜨리면 빌드가 깨진다. 값 방식에서 있던 안전장치를 이렇게 되찾았다.

**2. 스택트레이스를 끈다** (`writableStackTrace = false`, 생성자 마지막 인자)

`fillInStackTrace()`는 스택 깊이에 비례하는 비용이고 스프링 MVC 스택은 깊다.
재고가 소진된 뒤의 트래픽은 **전부** 이 경로로 흐른다.
초당 수백 번 나는 정상적인 결과에 그 비용을 낼 이유가 없다.

**3. `OrderException`을 상속하지 않으면 500이다**

이게 4xx와 5xx를 가르는 **유일한 선**이다.
`Inventory.addReserved`의 음수 검사는 `IllegalArgumentException`으로 남겼다.

**4. 프레임워크를 상속하지 않는다**

스프링의 `ErrorResponseException`을 상속하면 편하지만,
그 순간 `order-api`가 스프링에 묶여서 M4 물리 분리가 죽는다.
ArchUnit 규칙 `order-api는 어떤 프레임워크도 알지 못한다`가 이를 막는다.

HTTP 변환은 `bootstrap`의 `ProblemDetailAdvice` 한 곳에서만 일어난다.

## 대안

### (가) `DomainResult<E, T>`로 값을 반환 — 8/23에 채택했다가 8/24에 버림

실패를 예외가 아니라 **값**으로 돌려주는 방식이다. Rust의 `Result`, Kotlin Arrow의 `Either`와 같은 모양이다.

```kotlin
fun place(command: PlaceOrderCommand): DomainResult<OrderError, PlaceOrder>
```

장점이 분명했다. 시그니처만 봐도 어떤 실패가 가능한지 보이고,
`when`으로 분기하면 컴파일러가 누락을 잡아준다. 예외가 제어 흐름에 끼지 않는다.

**실제로 만들어서 하루 동안 돌렸다. 그리고 롤백 때문에 버렸다.**

> **스프링은 예외를 보고 롤백을 결정한다. 반환값은 보지 않는다.**

`DomainResult.failure(...)`를 `return` 하는 것은 **정상 종료**다.
`@Transactional`은 아무 일도 없었다고 판단하고 그대로 커밋한다.

실측한 증상: 재고 1로 두고 주문을 두 번 넣었더니
**성공은 1건인데 `orders` 테이블에 2행이 남았다.** 실패했다고 응답한 주문이 DB에 있었다.

막을 방법은 있었다. 서비스를 **읽기·계산 구간**과 **쓰기 구간**으로 갈라서,
쓰기 전에 모든 검증을 끝내면 되돌릴 것이 없다. 실제로 그렇게 고쳤고 동작했다.

버린 이유는 **그 규율이 유지될 것 같지 않아서**다.
지금은 검증이 전부 앞에 모이지만, M2 원장(ledger)은 쓰기가 흐름 중간에 낀다 —
원장 기입 → 잔액 확인 → 추가 기입 같은 순서가 자연스럽다.
그때 "쓰기 전에 다 검증한다"를 지키려면 코드를 부자연스럽게 비틀어야 한다.

**규율로 지켜야 하는 안전장치는 언젠가 깨진다.** 예외를 쓰면 프레임워크가 대신 지켜준다.

나중에 갈수록 시그니처가 더 많이 흔들리므로 M1에서 미리 갈아탔다.

### (나) `kotlin.Result<T>`

표준 라이브러리에 이미 있다. 하지만 실패 타입이 `Throwable`로 고정돼 있어서
`OutOfStock(productId, requested, available)` 같은 구조화된 에러를 담을 수 없다.
담으려면 결국 예외를 만들어야 하는데, 그러면 값으로 표현한 의미가 없다.
후보에서 일찍 탈락했다.

### (다) 값을 유지하면서 `setRollbackOnly()`로 롤백

```kotlin
TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
```

값 방식을 유지하면서 롤백만 얻는 길이었다. 두 가지 이유로 버렸다.

1. 스프링 내부 API가 애플리케이션 서비스에 들어온다. 트랜잭션 관리 방식이 도메인 흐름에 샌다
2. **실패를 반환하는 모든 곳에서 이 호출을 빠뜨리면 안 된다.** (가)와 같은 종류의 문제다 —
   사람이 지켜야 하는 규율이 하나 더 늘어난다

## 결과

### 얻은 것

- **롤백이 프레임워크의 일이 됐다.** `@Transactional`이 알아서 한다
- 서비스 코드가 짧아졌다. 읽기/쓰기 분할이 필요 없어졌다
- 컨트롤러에서 실패 처리가 사라졌다. 애노테이션 23줄이 1줄이 됐다
- 반환 타입이 `ResponseEntity<Any>`에서 `ResponseEntity<PlaceOrder>`로 좁혀져서
  OpenAPI 명세에 성공 스키마가 살아났다

  > ※ 이 마지막 항목은 **따라온 이득이지 뒤집은 이유가 아니다.** 이유는 롤백 하나다

### 포기한 것

- **시그니처가 실패를 말하지 않는다.** `place(cmd): PlaceOrder`만 보고는
  어떤 실패가 가능한지 알 수 없다. Kotlin에는 checked exception이 없어
  컴파일러가 강제해주지도 않는다. `@throws` KDoc으로 적었지만 **강제되지 않는 계약**이다
- **테스트가 길어졌다.** `data class`의 `equals`가 사라져서
  `assertEquals(예상에러, 결과)`를 못 쓴다. `assertFailsWith<T>` + 필드 단언으로 나눠야 한다
- 예외를 던지는 지점이 코드에 흩어진다. 값 방식은 반환 경로 하나로 모였다

### 되찾은 것

| 잃은 것 | 되찾은 방법 |
|---|---|
| `when` 누락 검사 | 계층을 `sealed`로 유지. 어드바이스가 상위 하나만 잡고 안에서 `when` |
| 스택트레이스 비용 없음 | `writableStackTrace = false` |

### 증거

말로만 두지 않고 테스트로 못 박았다.
`OrderPlacementService.place()`는 **주문을 먼저 저장하고 재고를 나중에 검사한다.**
일부러 그 순서다. 재고가 모자라면 이미 저장한 행까지 롤백된다.

`OrderRollbackIntegrationTest`가 이 경로를 검사한다 —
409를 받았을 때 `orders`가 비어 있는지. 값 방식이었다면 여기서 주문 행이 남았다.

`@Transactional(noRollbackFor = [OrderException::class])`를 임시로 붙여
이 테스트가 실제로 빨간불이 되는 것도 확인했다. **통과가 우연이 아니다.**

## 관련

- 설계 근거와 실측: [M1 §4-2](../milestones/M1-order-core.md)
- 커밋: `be79e97` — `git show be79e97`로 전환 전후 대조
- 구현: `order-api/.../OrderException.kt`, `bootstrap/.../OrderProblems.kt`, `bootstrap/.../ProblemDetailAdvice.kt`
- 증거 테스트: `bootstrap/src/test/.../OrderRollbackIntegrationTest.kt`
- 버린 코드: `git show 9367f2b:backend/modules/common/src/main/kotlin/com/commercelab/common/DomainResult.kt`
