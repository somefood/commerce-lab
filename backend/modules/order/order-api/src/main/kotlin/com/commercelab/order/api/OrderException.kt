package com.commercelab.order.api

/**
 * 예상된 비즈니스 실패. "일어날 수 있고, 호출자에게 알려줘야 하는 일"이다.
 *
 * ## 왜 예외인가 (2026-08-24, DomainResult에서 전환)
 *
 * 이전에는 `DomainResult<OrderError, T>`로 실패를 값으로 돌려줬다. 바꾼 이유는 하나다 —
 * **스프링은 예외를 보고 롤백을 결정한다. 반환값은 보지 않는다.**
 * 값으로 돌려주면 실패해도 트랜잭션이 커밋되므로, 서비스를 "읽기 구간 / 쓰기 구간"으로
 * 갈라서 쓰기 전에 모든 검증을 끝내야 했다. 그 제약이 M2 원장처럼
 * 쓰기가 흐름 중간에 끼는 곳에서는 유지되기 어렵다.
 *
 * ## 값 방식에서 잃은 것과 되찾은 방법
 *
 * `catch`는 `when`과 달리 케이스를 빠뜨려도 컴파일러가 잡아주지 않는다.
 * 그래서 이 계층을 **sealed로 유지한다.** 어드바이스에서 `OrderException` 하나로 받고
 * 안에서 `when`으로 분기하면, 하위 클래스를 추가했을 때 매핑을 빠뜨리면 빌드가 깨진다.
 * (`bootstrap/web/order/OrderProblems.kt`가 그 지점이다)
 *
 * 잃은 채로 두는 것도 있다. `data class`의 `equals`가 사라져서
 * 테스트가 `assertEquals(에러객체, 결과)`를 못 쓴다. 타입과 필드를 따로 단언해야 한다.
 * 예외를 값처럼 비교하는 것은 애초에 예외의 용법이 아니므로 감수한다.
 *
 * ## 스택트레이스를 끄는 이유
 *
 * `RuntimeException(message, cause, enableSuppression, writableStackTrace)`의
 * 마지막 인자가 `false`다. `fillInStackTrace()`는 스택 깊이에 비례하는 비용이고
 * 스프링 MVC 스택은 깊다. 재고가 소진된 뒤의 트래픽은 **전부** 이 경로로 흐른다 —
 * 초당 수백 번 발생하는 정상적인 비즈니스 결과에 그 비용을 낼 이유가 없다.
 *
 * 대가: 스택트레이스가 없어 "어디서 났는지" 볼 수 없다. 예상된 실패는 그걸 알 필요가 없다.
 * 무엇이 일어났는지는 타입과 필드가 말해준다. 예상하지 못한 예외는 이 계층 밖이고,
 * 그쪽 스택트레이스는 그대로 살아 있다.
 *
 * ## 이 계층에 넣지 않는 것
 *
 * **"일어나면 버그인 것"은 여기 상속시키지 않는다.** 그게 이 계층의 존재 이유다.
 * `Inventory.addReserved`의 음수 검사는 `IllegalArgumentException`으로 남는다.
 * 음수 수량이 도메인까지 내려온 것은 위쪽 검증이 뚫렸다는 뜻이고, 그건 500이 맞다.
 * `ProblemDetailAdvice`가 `OrderException`만 4xx로 바꾸고 나머지는 500으로 흘린다 —
 * 그 경계가 "예상된 실패"와 "사고"를 가르는 유일한 선이다.
 *
 * 프레임워크를 상속하지 않는 이유(`ErrorResponseException` 등):
 * order-api에 스프링이 들어오면 M4 물리 분리가 죽는다. ArchUnit이 이를 강제한다.
 */
sealed class OrderException(message: String) : RuntimeException(message, null, false, false) {

    /** 재고 부족. 반품·입고·선점 만료로 달라질 수 있다 → 409 */
    class OutOfStock(
        val productId: String,
        val requested: Int,
        val available: Int,
    ) : OrderException("재고 부족: product=$productId requested=$requested available=$available")

    /** 재시도 한도를 넘긴 낙관적 락 충돌(2단계). 다시 시도하면 될 수 있다 → 409 */
    class ConflictExhausted(
        val attempts: Int,
    ) : OrderException("동시 요청 충돌: ${attempts}회 재시도 후 포기")

    /** 이미 확정되었거나 만료된 선점(3단계) → 409 */
    class ReservationAlreadySettled(
        val reservationId: String,
    ) : OrderException("이미 처리된 선점: $reservationId")

    /** 허용되지 않는 상태 전이 → 409 */
    class InvalidStatusTransition(
        val from: OrderStatus,
        val to: OrderStatus,
    ) : OrderException("허용되지 않는 상태 전이: $from -> $to")

    /**
     * 판매 중지된 상품이다 → 409
     *
     * 404가 아닌 이유: 상품은 존재한다. 지금 상태가 요청과 맞지 않을 뿐이고,
     * 다시 활성화되면 같은 요청이 성공한다. 그게 409의 뜻이다.
     * 400도 아니다 — 요청 자체는 멀쩡하다.
     */
    class ProductInactive(
        val productId: String,
    ) : OrderException("판매 중지된 상품: $productId")

    /** 상품이 없다 → 404 */
    class ProductNotFound(
        val productId: String,
    ) : OrderException("상품을 찾을 수 없음: $productId")

    /** 주문이 없다 → 404 */
    class OrderNotFound(
        val orderId: String,
    ) : OrderException("주문을 찾을 수 없음: $orderId")

    /** 수량이 1 미만이다. 그대로 다시 보내도 똑같이 실패한다 → 400 */
    class InvalidQuantity(
        val productId: String,
        val quantity: Int,
    ) : OrderException("잘못된 수량: product=$productId quantity=$quantity")

    /**
     * 라인이 하나도 없다 → 400
     *
     * 필드가 없는데 `data object`가 아닌 이유:
     * `object`로 만들면 모든 요청이 **같은 예외 인스턴스 하나**를 던진다.
     * `Throwable`은 `cause`와 `suppressed`를 나중에 붙일 수 있는 가변 객체라,
     * 어디선가 `initCause`를 부르거나 스택트레이스를 다시 켜는 순간
     * 요청 사이로 상태가 샌다. 객체 하나 아끼자고 낼 위험이 아니다.
     * (같은 이유로 [ReservationAlreadySettled]도 `data object`에서 클래스로 바꿨다)
     */
    class EmptyOrder : OrderException("주문 항목이 하나도 없음")
}
