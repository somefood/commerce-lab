package com.commercelab.product.adapter.out.persistence

import com.commercelab.common.Money
import com.commercelab.product.application.port.out.ProductRepository
import com.commercelab.product.domain.Product
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
        return productJpaRepository.findById(id)
            .getOrNull<ProductJpaEntity>()?.toDomain()
    }

    override fun findAll(): List<Product> {
        return productJpaRepository.findAll().map { it.toDomain() }
    }
}

fun Product.toEntity(): ProductJpaEntity {
    return ProductJpaEntity(
        name = name,
        price = price.amount,
        description = description,
        stockQuantity = stockQuantity,
        status = status
    )
}

fun ProductJpaEntity.toDomain(): Product {
    return Product(
        id = id,
        name = name,
        price = Money.of(price),
        description = description,
        stockQuantity = stockQuantity,
        status = status
    )
}