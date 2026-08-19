package com.commercelab.order

import ch.qos.logback.core.spi.ErrorCodes
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
    val status: OrderStatus,
    val totalAmount: Money
) {

    companion object {
        fun place(orderId: String, accountId: String, lines: List<OrderLine>, now: Instant)
                : DomainResult<OrderError, Order> {

            val totalAmount = calculateTotalAmount(lines)

            if (validateOrderLines(lines)) {
                return DomainResult.failure(OrderError.ReservationAlreadySettled)
            }

            if (validateOrderLines2(lines)) {
                return DomainResult.failure(OrderError.InvalidQuantity("p-1", 0))
            }

            val order = Order(
                orderId = orderId,
                accountId = accountId,
                lines = lines,
                placedAt = now,
                status = OrderStatus.CREATED,
                totalAmount = totalAmount,
            )


            return DomainResult.success(order)
        }

        private fun validateOrderLines2(lines: List<OrderLine>): Boolean {
            val allMatch = lines.stream().allMatch { it -> it.quantity <= 0 }
            if (allMatch) {
                return true
            }
            return false
        }

        private fun validateOrderLines(lines: List<OrderLine>): Boolean {
            if (lines.isEmpty()) {
                return true
            }

            return false;
        }

        private fun calculateTotalAmount(lines: List<OrderLine>): Money {
            return lines.fold(Money.ZERO) { acc, line ->
                acc + line.unitAmount * line.quantity
            }
        }
    }

    fun markPaid(now: Instant): DomainResult<OrderError, Order> {

        if (status == OrderStatus.CANCELLED) {
            return DomainResult.failure(OrderError.InvalidStatusTransition(OrderStatus.CANCELLED, OrderStatus.PAID))
        }

        if (status == OrderStatus.CREATED) {
            return DomainResult.success(copy(status = OrderStatus.PAID))
        }

        return DomainResult.failure(OrderError.InvalidStatusTransition(OrderStatus.CANCELLED, OrderStatus.PAID))
    }

    fun cancel(now: Instant): DomainResult<OrderError, Order> {
        if (status == OrderStatus.PAID) {
            return DomainResult.success(copy(status = OrderStatus.CANCELLED))
        }

        if (status == OrderStatus.CANCELLED) {
            return DomainResult.failure(OrderError.InvalidStatusTransition(OrderStatus.CANCELLED, OrderStatus.CANCELLED))
        }

        return DomainResult.success(copy(status = OrderStatus.CANCELLED))
    }
}

data class OrderLine(
    val productId: String,
    val quantity: Int,
    val unitAmount: Money
)