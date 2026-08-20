package com.commercelab.order.infrastructure

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Table(schema = "\"order\"", name = "orders")
@Entity
class OrderEntity(
    @Id var id: String,
    var accountId: String,
    var status: String,
    var totalAmount: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
    var version: Int,
) {
}
