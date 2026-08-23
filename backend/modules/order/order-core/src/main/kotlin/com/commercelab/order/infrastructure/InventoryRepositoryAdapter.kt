package com.commercelab.order.infrastructure

import com.commercelab.order.domain.Inventory
import com.commercelab.order.domain.InventoryRepository
import org.springframework.stereotype.Repository

@Repository
class InventoryRepositoryAdapter(
    private val inventoryJpaRepository: InventoryJpaRepository
) : InventoryRepository {

    override fun findByProductId(productId: String): Inventory {
        val findByProductId = inventoryJpaRepository.findByProductId(productId)
            ?: throw IllegalArgumentException("Product not found: $productId")
        return findByProductId.toDomain()
    }

    override fun updateReserveQuantity(inventory: Inventory) {
        val inventoryEntity = inventoryJpaRepository.findByProductId(inventory.productId) ?: return
        inventoryEntity.reserved = inventory.reserved
    }
}

fun InventoryEntity.toDomain(): Inventory {
    return Inventory(
        productId = productId,
        total = total,
        reserved = reserved,
        version = version
    )
}