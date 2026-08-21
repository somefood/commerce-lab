package com.commercelab.order.infrastructure

import com.commercelab.common.Money
import com.commercelab.order.domain.Product
import com.commercelab.order.domain.ProductRepository
import org.springframework.stereotype.Repository

@Repository
class ProductRepositoryAdapter(
    private val productJpaRepository : ProductJpaRepository
) : ProductRepository {

    override fun findByIds(ids: List<String>): List<Product> = productJpaRepository.findAllById(ids).map { it.toDomain() }
}

private fun ProductEntity.toDomain(): Product {
    return Product(
        id = id,
        unitAmount = Money.of(unitAmount)
    )
}