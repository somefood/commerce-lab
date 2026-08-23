package com.commercelab.order.infrastructure

import com.commercelab.common.Money
import com.commercelab.order.api.OrderStatus
import com.commercelab.order.domain.Order
import com.commercelab.order.domain.OrderLine
import com.commercelab.order.domain.OrderRepository
import org.springframework.stereotype.Repository

@Repository
class OrderRepositoryAdapter(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderLineRepository: OrderLineRepository,
) : OrderRepository {

    override fun save(order: Order): Order {
        val orderEntity = OrderEntity(
            id = order.orderId,
            accountId = order.accountId,
            status = order.status.toString(),
            totalAmount = order.totalAmount.amount,
            createdAt = order.placedAt,
            updatedAt = order.updatedAt,
            version = 1
        )

        val save = orderJpaRepository.save(orderEntity)

        val savedOrderLines = orderLineRepository.saveAll(order.lines.map { line -> OrderLineEntity(
            id = null,
            orderId = save.id,
            productId = line.productId,
            quantity = line.quantity,
            unitAmount = line.unitAmount.amount,
        )})

        return save.toDomain(savedOrderLines)
    }
}

private fun OrderEntity.toDomain(savedOrderLines: List<OrderLineEntity>): Order {
    return Order(
        orderId = id,
        accountId = accountId,
        status = OrderStatus.valueOf(status),
        totalAmount = Money.of(totalAmount),
        placedAt = createdAt,
        updatedAt = updatedAt,
        lines = savedOrderLines.map { it.toDomain() }
    )
}

private fun OrderLineEntity.toDomain(): OrderLine {
    return OrderLine(
        productId = productId,
        quantity = quantity,
        unitAmount = Money.of(unitAmount)
    )
}