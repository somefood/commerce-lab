package com.commercelab.order.infrastructure

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Table(schema = "\"order\"", name = "products")
@Entity
class ProductEntity(
    @Id var id: String,
    var name: String,
    var unitAmount: Long,
    var active: Boolean,
) {
}
