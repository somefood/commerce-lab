package com.commercelab.order.api

import java.time.Instant

data class PlaceOrderCommand(
    val accountId: String,
    val lines: List<Line>,
) {
    data class Line(val productId: String, val quantity: Int)
}

data class PlaceOrder(
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

/**
 * 주문 생성 포트. 외부에서 order 모듈로 들어오는 유일한 입구다.
 *
 * 반환 타입이 성공 케이스만 말한다. 실패는 [OrderException]으로 나간다.
 *
 * 시그니처만 보고 어떤 실패가 가능한지 알 수 없다는 것이 이 방식의 대가다.
 * Kotlin에는 checked exception이 없어 컴파일러가 강제해주지도 않는다.
 * 그래서 @throws로 적는다 — 강제되지 않는 계약이므로 사람이 관리해야 한다.
 */
interface OrderPlacement {
    /**
     * @throws OrderException.EmptyOrder 라인이 하나도 없을 때
     * @throws OrderException.InvalidQuantity 라인 수량이 1 미만일 때
     * @throws OrderException.ProductNotFound 라인의 상품이 없을 때
     * @throws OrderException.OutOfStock 주문 가능 수량이 모자랄 때
     */
    fun place(command: PlaceOrderCommand): PlaceOrder
}

interface OrderQuery {
    /** @throws OrderException.OrderNotFound 주문이 없을 때 */
    fun findById(orderId: String): PlaceOrder
}
