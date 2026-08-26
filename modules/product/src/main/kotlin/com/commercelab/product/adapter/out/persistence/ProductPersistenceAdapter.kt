package com.commercelab.product.adapter.out.persistence

import com.commercelab.product.application.port.out.ProductRepository
import com.commercelab.product.domain.Product
import com.commercelab.product.domain.ProductStatus
import org.springframework.stereotype.Repository
import kotlin.jvm.optionals.getOrNull

@Repository
class ProductPersistenceAdapter(
    private val productJpaRepository: ProductJpaRepository
) : ProductRepository {

    override fun save(product: Product): Product {
        val entity = product.toEntity()
        productJpaRepository.save(entity)
        return product.copy(id = entity.id)
    }

    override fun findById(id: Long): Product? {
        val orNull = productJpaRepository.findById(id)
            .getOrNull()

        if (orNull == null) {
            return orNull
        }

        return orNull.toDomain()
    }

    override fun findAll(): List<Product> {
        return productJpaRepository.findAll().map { it.toDomain() }
    }
}

fun Product.toEntity(): ProductJpaEntity {
    return ProductJpaEntity(
        id = id,
        name = name,
        price = price,
        description = description,
        stockQuantity = stockQuantity,
        status = status.name
    )
}

fun ProductJpaEntity.toDomain(): Product {
    return Product(
        id = id,
        name = name,
        price = price,
        description = description,
        stockQuantity = stockQuantity,
        status = ProductStatus.valueOf(status)
    )
}