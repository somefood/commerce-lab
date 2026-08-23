package com.commercelab.order.domain

interface InventoryRepository {

    fun findByProductId(productId: String): Inventory

    fun updateReserveQuantity(inventory: Inventory)
}