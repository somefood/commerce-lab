package com.commercelab.product.domain

import com.commercelab.product.adapter.`in`.web.ProductCreateRequest

object ProductFixture {
    fun product(
        name: String = "테스트 상품",
        price: Long = 10_000L,
        description: String = "설명",
        stockQuantity: Int = 100,
    ): Product = Product.create(name, price, description, stockQuantity)

    fun createRequest(
        name: String = "테스트 상품",
        price: Long = 10_000L,
        description: String = "설명",
        stockQuantity: Int = 100,
    ): ProductCreateRequest = ProductCreateRequest(name, price, description, stockQuantity)
}