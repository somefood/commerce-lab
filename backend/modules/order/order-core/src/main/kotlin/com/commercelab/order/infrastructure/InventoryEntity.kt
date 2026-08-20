package com.commercelab.order.infrastructure

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Table(schema = "\"order\"", name = "inventories")
@Entity
class InventoryEntity(
    @Id var productId: String,
    var total: Int,
    var reserved: Int,
    var version: Int,
) {
}
