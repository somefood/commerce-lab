package com.commercelab.product.application.port.out

import com.commercelab.product.domain.Product

interface ProductRepository {
    fun save(product: Product): Product
    fun findById(id: Long): Product?
    fun findAll(): List<Product>
}