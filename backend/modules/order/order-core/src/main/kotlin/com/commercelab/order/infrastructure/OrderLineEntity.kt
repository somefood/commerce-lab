package com.commercelab.order.infrastructure

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Table(schema = "\"order\"", name = "order_lines")
@Entity
class OrderLineEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long,
    var orderId: String,
    var productId: String,
    var quantity: Int,
    var unitAmount: Long,
) {
}
