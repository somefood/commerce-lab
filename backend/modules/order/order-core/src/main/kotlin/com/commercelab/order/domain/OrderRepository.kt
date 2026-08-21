package com.commercelab.order.domain

interface OrderRepository {
    fun save(placedOrder: Order): Order
}