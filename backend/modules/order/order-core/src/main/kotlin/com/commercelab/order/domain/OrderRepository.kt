package com.commercelab.order.domain

interface OrderRepository {
    fun save(order: Order): Order
}