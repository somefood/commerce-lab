package com.commercelab.product.application.service

import com.commercelab.product.application.port.out.ProductRepository
import com.commercelab.product.domain.Product

class FakeProductRepository : ProductRepository {

    private val store = LinkedHashMap<Long, Product>()
    private var sequence = 0L

    override fun save(product: Product): Product {
        val id = product.id ?: ++sequence
        val saved = product.copy(id = id)
        store[id] = saved
        return saved
    }

    override fun findById(id: Long): Product? = store[id]

    override fun findAll(): List<Product> = store.values.toList()

    // --- 테스트 편의용 ---
    fun seed(vararg products: Product): List<Product> = products.map(::save)

    fun clear() {
        store.clear()
        sequence = 0L
    }

    val size: Int get() = store.size
}
