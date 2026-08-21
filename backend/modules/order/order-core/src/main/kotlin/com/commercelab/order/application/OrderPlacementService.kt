package com.commercelab.order.application

import com.commercelab.common.DomainResult
import com.commercelab.order.api.OrderError
import com.commercelab.order.api.OrderPlacement
import com.commercelab.order.api.PlaceOrder
import com.commercelab.order.api.PlaceOrderCommand
import com.commercelab.order.domain.Order
import com.commercelab.order.domain.OrderLine
import com.commercelab.order.domain.OrderRepository
import com.commercelab.order.domain.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*


@Service
class OrderPlacementService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
) : OrderPlacement {

    @Transactional
    override fun place(command: PlaceOrderCommand): DomainResult<OrderError, PlaceOrder> {
        val products = productRepository.findByIds(command.lines.map { it.productId })

        val orderLines = mutableListOf<OrderLine>()
        command.lines.forEach { line ->
            val product = products.find { it.id == line.productId }
            if (product != null) {
                orderLines.add(OrderLine(
                    productId = line.productId,
                    quantity = line.quantity,
                    unitAmount = product.unitAmount
                ))
            }
        }

        val order = when (val result = Order.place(
            orderId = UUID.randomUUID().toString(),
            accountId = command.accountId,
            lines = orderLines,
            now = Instant.now(),
        )) {
            is DomainResult.Failure -> return result // 조기 반환
            is DomainResult.Success -> result.value
        }

        orderRepository.save(order)

        return DomainResult.Success(
            PlaceOrder(
                orderId = order.orderId,
                status = order.status,
                totalAmount = order.totalAmount.amount,
                reservations = emptyList()
            )
        )
    }
}