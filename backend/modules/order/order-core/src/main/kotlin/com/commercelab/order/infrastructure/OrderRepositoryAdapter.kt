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
            orderId = order.orderId,
            accountId = order.accountId,
            status = order.status.toString(),
            totalAmount = order.totalAmount.amount,
            createdAt = order.placedAt,
            updatedAt = order.updatedAt,
            // TODO(2단계): @Version을 붙일지 조건부 UPDATE로 갈지 정해야 한다 (ADR-0003).
            //  지금은 그냥 정수 컬럼이라 하드코딩해도 아무 일도 일어나지 않는다 —
            //  그게 문제다. 낙관적 락을 켜는 순간 이 1이 덮어쓰기를 만든다.
            version = 1,
        )

        val saved = orderJpaRepository.save(orderEntity)

        // saved.getId() 대신 order.orderId를 쓴다. 어차피 같은 값이고,
        // 저장 결과에서 다시 꺼내면 "저장이 id를 바꿀 수도 있다"는 인상을 준다.
        val savedLines = orderLineRepository.saveAll(
            order.lines.map { line ->
                OrderLineEntity(
                    id = null,
                    orderId = order.orderId,
                    productId = line.productId,
                    quantity = line.quantity,
                    unitAmount = line.unitAmount.amount,
                )
            }
        )

        return saved.toDomain(savedLines)
    }
}

private fun OrderEntity.toDomain(savedOrderLines: List<OrderLineEntity>): Order {
    return Order(
        orderId = getId(),
        accountId = accountId,
        status = OrderStatus.valueOf(status),
        totalAmount = Money.of(totalAmount),
        placedAt = createdAt,
        updatedAt = updatedAt,
        lines = savedOrderLines.map { it.toDomain() },
    )
}

private fun OrderLineEntity.toDomain(): OrderLine {
    return OrderLine(
        productId = productId,
        quantity = quantity,
        unitAmount = Money.of(unitAmount),
    )
}
