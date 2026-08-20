package com.commercelab.order

import com.commercelab.common.DomainResult
import com.commercelab.common.Money
import com.commercelab.order.api.OrderError
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
        fun place(orderId: String, accountId: String, lines: List<OrderLine>, now: Instant)
                : DomainResult<OrderError, Order> {

            if (lines.isEmpty()) {
                return DomainResult.failure(OrderError.EmptyOrder)
            }

            val invalidLine = firstInvalidLine(lines)
            if (invalidLine != null) {
                return DomainResult.failure(
                    OrderError.InvalidQuantity(invalidLine.productId, invalidLine.quantity)
                )
            }

            val totalAmount = calculateTotalAmount(lines)
            val order = Order(
                orderId = orderId,
                accountId = accountId,
                lines = lines,
                placedAt = now,
                updatedAt = now,
                status = OrderStatus.CREATED,
                totalAmount = totalAmount,
            )

            return DomainResult.success(order)
        }

        private fun firstInvalidLine(lines: List<OrderLine>): OrderLine? =
            lines.firstOrNull { it.quantity <= 0 }

        private fun calculateTotalAmount(lines: List<OrderLine>): Money {
            return lines.fold(Money.ZERO) { acc, line ->
                acc + line.unitAmount * line.quantity
            }
        }
    }

    fun markPaid(now: Instant): DomainResult<OrderError, Order> {
        return when (status) {
            OrderStatus.CREATED -> DomainResult.success(copy(status = OrderStatus.PAID, updatedAt = now))
            OrderStatus.PAID,
            OrderStatus.CANCELLED,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED ->
                DomainResult.failure(OrderError.InvalidStatusTransition(status, OrderStatus.PAID))
        }
    }

    fun cancel(now: Instant): DomainResult<OrderError, Order> {
        return when (status) {
            OrderStatus.CREATED,
            OrderStatus.PAID ->
                DomainResult.success(copy(status = OrderStatus.CANCELLED, updatedAt = now))

            OrderStatus.CANCELLED,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED ->
                DomainResult.failure(OrderError.InvalidStatusTransition(status, OrderStatus.CANCELLED))
        }
    }
}

data class OrderLine(
    val productId: String,
    val quantity: Int,
    val unitAmount: Money
)
