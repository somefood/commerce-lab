package com.commercelab.order.domain

import com.commercelab.common.Money
import com.commercelab.order.api.OrderException
import com.commercelab.order.api.OrderStatus
import java.time.Instant

data class Order(
    val orderId: String,
    val accountId: String,
    val lines: List<OrderLine>,
    val placedAt: Instant,
    val updatedAt: Instant,
    val status: OrderStatus,
    val totalAmount: Money
) {

    companion object {
        /**
         * @throws OrderException.EmptyOrder
         * @throws OrderException.InvalidQuantity
         */
        fun place(orderId: String, accountId: String, lines: List<OrderLine>, now: Instant): Order {

            if (lines.isEmpty()) {
                throw OrderException.EmptyOrder()
            }

            val invalidLine = firstInvalidLine(lines)
            if (invalidLine != null) {
                throw OrderException.InvalidQuantity(invalidLine.productId, invalidLine.quantity)
            }

            return Order(
                orderId = orderId,
                accountId = accountId,
                lines = lines,
                placedAt = now,
                updatedAt = now,
                status = OrderStatus.CREATED,
                totalAmount = calculateTotalAmount(lines),
            )
        }

        private fun firstInvalidLine(lines: List<OrderLine>): OrderLine? =
            lines.firstOrNull { it.quantity <= 0 }

        private fun calculateTotalAmount(lines: List<OrderLine>): Money {
            return lines.fold(Money.ZERO) { acc, line ->
                acc + line.unitAmount * line.quantity
            }
        }
    }

    /**
     * when을 **식(expression)으로** 쓰는 것이 중요하다.
     * 그래야 컴파일러가 OrderStatus의 모든 값을 다뤘는지 검사한다(exhaustive).
     * 상태가 추가되면 여기서 컴파일이 깨진다 — 실패를 값에서 예외로 바꿔도
     * 이 안전장치는 그대로 남는다. throw는 Nothing 타입이라 식의 한 갈래가 될 수 있다.
     *
     * @throws OrderException.InvalidStatusTransition
     */
    fun markPaid(now: Instant): Order = when (status) {
        OrderStatus.CREATED -> copy(status = OrderStatus.PAID, updatedAt = now)
        OrderStatus.PAID,
        OrderStatus.CANCELLED,
        OrderStatus.SHIPPED,
        OrderStatus.DELIVERED ->
            throw OrderException.InvalidStatusTransition(status, OrderStatus.PAID)
    }

    /** @throws OrderException.InvalidStatusTransition */
    fun cancel(now: Instant): Order = when (status) {
        OrderStatus.CREATED,
        OrderStatus.PAID -> copy(status = OrderStatus.CANCELLED, updatedAt = now)

        OrderStatus.CANCELLED,
        OrderStatus.SHIPPED,
        OrderStatus.DELIVERED ->
            throw OrderException.InvalidStatusTransition(status, OrderStatus.CANCELLED)
    }
}

data class OrderLine(
    val productId: String,
    val quantity: Int,
    val unitAmount: Money
)
