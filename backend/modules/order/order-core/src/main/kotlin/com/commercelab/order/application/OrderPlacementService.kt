package com.commercelab.order.application

import com.commercelab.common.DomainResult
import com.commercelab.order.api.OrderError
import com.commercelab.order.api.OrderPlacement
import com.commercelab.order.api.PlaceOrder
import com.commercelab.order.api.PlaceOrderCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class OrderPlacementService : OrderPlacement {

    @Transactional
    override fun place(command: PlaceOrderCommand): DomainResult<OrderError, PlaceOrder> {
        TODO("Not yet implemented")
    }
}