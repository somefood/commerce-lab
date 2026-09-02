package com.commercelab.product.adapter.out.persistence

import com.commercelab.product.domain.ProductStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "products")
class ProductJpaEntity(
    var name: String,
    var price: Long,
    var description: String?,
    var stockQuantity: Int,

    @Enumerated(EnumType.STRING)
    var status: ProductStatus
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}