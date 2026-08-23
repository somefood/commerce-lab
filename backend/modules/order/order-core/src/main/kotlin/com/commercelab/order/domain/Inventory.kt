package com.commercelab.order.domain

import com.commercelab.common.DomainResult
import com.commercelab.order.api.OrderError

data class Inventory(
    val productId: String,
    val total: Int,
    val reserved: Int,
    val version: Int,
) {
    fun addReserved(newReserve: Int): DomainResult<OrderError, Inventory> {
        if (newReserve < 0) throw IllegalArgumentException("Reserved quantity cannot be negative")
        if (reserved + newReserve > total) return DomainResult.failure(OrderError.OutOfStock(productId, newReserve, total - reserved))
        return DomainResult.success(copy(reserved = reserved + newReserve))
    }
}
