package com.commercelab.product.application.port.`in`

import com.commercelab.product.domain.Product

interface GetProductQuery {

    fun getProduct(id: Long): Product

    fun getAllProducts(): List<Product>
}