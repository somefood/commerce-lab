package com.commercelab.order.api

import com.commercelab.common.DomainResult
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
 * kotlin.Result가 아니라 DomainResult를 쓰는 이유는 DomainResult의 주석에 있다.
 * 요약하면 실패 타입이 Throwable로 고정되면 OrderError를 담을 수 없다.
 */
interface OrderPlacement {
    fun place(command: PlaceOrderCommand): DomainResult<OrderError, PlaceOrder>
}

interface OrderQuery {
    fun findById(orderId: String): DomainResult<OrderError, PlaceOrder>
}
