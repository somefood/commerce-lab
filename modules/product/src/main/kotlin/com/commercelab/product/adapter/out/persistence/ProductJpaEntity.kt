package com.commercelab.product.adapter.out.persistence

import com.commercelab.product.domain.ProductStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class ProductJpaEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val name: String,
    val price: Long,
    val description: String?,
    val stockQuantity: Int,

    @Enumerated(EnumType.STRING)
    val status: ProductStatus
) {
}