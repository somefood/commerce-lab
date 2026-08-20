package com.commercelab.order.api

sealed interface OrderError {
    data class OutOfStock(val productId: String, val requested: Int, val available: Int) : OrderError
    data class ProductNotFound(val productId: String) : OrderError
    data class InvalidQuantity(val productId: String, val quantity: Int) : OrderError
    data class OrderNotFound(val orderId: String) : OrderError
    data class ConflictExhausted(val attempts: Int) : OrderError
    data object ReservationAlreadySettled : OrderError
    data class InvalidStatusTransition(val from: OrderStatus, val to: OrderStatus) : OrderError
    data object EmptyOrder : OrderError
}
