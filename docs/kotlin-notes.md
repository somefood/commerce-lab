# Kotlin 노트 — Java 8에서 넘어오면서

> **관련 문서**
> [진도](./PROGRESS.md) · [협업 규칙](../CLAUDE.md) · [용어 사전](./glossary.md) · [마일스톤](./milestones/)

이 레포 코드에서 만난 낯선 문법을 **Java 8이라면 어떻게 짰을지**와 나란히 기록한다.
Kotlin 강의를 따로 듣지 않는다 — 이 프로젝트에 필요한 관용구는 아래 목록이 거의 전부다.

## 규칙

- 리뷰나 구현 중 "이게 뭐지" 싶은 문법이 나오면 그 자리에서 한 절 추가한다
- **"Java 8이라면" 칸은 사용자가 쓴다.** 그게 이 노트의 존재 이유다 —
  이미 아는 언어에 걸어서 배우고, "Java에서 Kotlin으로 넘어오며 뭐가 좋았나"라는
  면접 단골 질문의 답을 쌓는다
- 실제 파일 경로를 적는다. 장난감 예제는 만들지 않는다

---

## 작성 예시 (Claude 작성. 이후는 사용자가 쓴다)

### sealed + else 없는 when

- **이 레포 어디에**: `order-api/.../OrderException.kt`,
  `bootstrap/.../OrderProblems.kt`의 `toProblemDetail`, `domain/Order.kt`의 `markPaid`

```kotlin
sealed class OrderException(message: String) : RuntimeException(message, null, false, false) {
    class OutOfStock(val productId: String, val requested: Int, val available: Int) : OrderException("...")
    class EmptyOrder : OrderException("...")
}

// 처리하는 쪽 — else가 없다
when (ex) {
    is OrderException.OutOfStock -> /* ex.productId 접근 가능 (스마트 캐스트) */
    is OrderException.EmptyOrder -> ...
    // 새 하위 클래스가 추가되면 여기서 컴파일이 깨진다
}
```

**sealed를 예외에 붙이는 이유가 여기 있다.** `catch (e: OutOfStock)`는 케이스를 빠뜨려도
컴파일러가 아무 말도 안 한다 — Kotlin에는 checked exception이 없다.
그래서 던지기는 예외로 던지되, 받는 곳에서는 상위 하나(`OrderException`)만 잡고
안에서 `when`으로 분기한다. 그러면 exhaustive 검사가 되살아난다.
(2026-08-24에 `DomainResult`를 버리고 예외로 갈 때 이게 협상 조건이었다. M1 §4-2)

- **Java 8이라면**: enum + switch로 흉내내면 케이스별로 다른 필드(`productId` 등)를 못 담는다.
  계층 구조로 만들면 `instanceof` 체인이 되고, 새 타입을 추가해도 컴파일러가 침묵한다
- **핵심 차이**: sealed는 "하위 타입이 이게 전부"라고 컴파일러에게 알려준다.
  그래서 when이 모든 경우를 다뤘는지 컴파일 시점에 검사된다.
  `OrderStatus`에 값이 추가되면 `Order.markPaid`가 컴파일 에러 — 이게 도메인 규칙 보호 장치다

---

## 이 레포에서 만나게 될 관용구 (만나면 위 형식으로 채운다)

- [ ] **data class + `copy()`** — `Order.cancel`이 필드 하나 바꾼 새 객체를 만드는 방법.
      Java의 setter/builder와 뭐가 다른가? 왜 전부 `val`인가?
      → `domain/Order.kt`
- [ ] **null 안전성 (`?.` `?:` `firstOrNull`)** — `firstInvalidLine`이 `OrderLine?`을 반환하는 이유.
      Java의 `Optional`과 뭐가 다른가?
      → `domain/Order.kt`
- [ ] **companion object** — `Order.place`가 생성자 대신 정적 팩토리인 이유.
      Java의 static과 뭐가 같고 다른가?
      → `domain/Order.kt`
- [ ] **고차함수와 `fold`** — `lines.fold(Money.ZERO) { acc, line -> ... }`.
      Java 8 Stream의 `reduce`와 비교해볼 것
      → `domain/Order.kt`
- [ ] **예외를 값처럼 다루기 vs 던지기** — 이 레포는 둘 다 해봤다.
      `DomainResult<E, T>`(8/23) → `OrderException`(8/24). 뒤집은 이유는 트랜잭션 롤백.
      Java의 checked exception이 하려던 일과, Kotlin이 그걸 버린 대가를 같이 볼 것
      → `order-api/.../OrderException.kt`, M1 §4-2, `git show`로 이전 버전 비교
- [ ] **컬렉션 관용구 (`map` `mapNotNull` `associateBy` `groupBy`)** — 상품 조회 리뷰 지적
      ("라인마다 리스트를 훑는다 → `associateBy`")이 여기서 나왔다.
      Java 8 Stream + Collectors와 나란히 놓고 볼 것
      → `application/OrderPlacementService.kt`
- [ ] **named / default arguments** — `Order(orderId = ..., accountId = ...)`.
      Java의 builder 패턴이 왜 Kotlin에선 거의 안 쓰이는가?
- [ ] **확장 함수** — `OrderException.toProblemDetail()`이 클래스 메서드가 아닌 이유.
      (`order-api`에 스프링을 못 들이므로 매핑은 bootstrap 쪽 확장 함수여야 한다)
      → `bootstrap/.../OrderProblems.kt`
- [ ] **제네릭 변성 (`out`, `Nothing`)** — 삭제된 `DomainResult`의 상단 주석에 설명이 있었다.
      `git show 9367f2b:backend/modules/common/src/main/kotlin/com/commercelab/common/DomainResult.kt`
      Java의 `? extends`와 대응시켜 볼 것. (어려우면 미뤄도 된다 — 쓰는 데는 지장 없다)
- [ ] **`throw`가 식(expression)이 되는 것** — `Order.markPaid`의 `when`이 식인데 한 갈래가 `throw`다.
      Kotlin에서 `throw`의 타입은 `Nothing`이라 어떤 타입 자리에도 들어간다. Java에서는 불가능하다
      → `domain/Order.kt`
