package com.commercelab.product.domain

import com.commercelab.product.application.port.`in`.RegisterProductCommand

object ProductFixture {
    fun product(
        name: String = "테스트 상품",
        price: Long = 10_000L,
        description: String = "설명",
        stockQuantity: Int = 100,
    ): Product = Product.create(name, price, description, stockQuantity)

    fun createRequestCommand(
        name: String = "테스트 상품",
        price: Long = 10_000L,
        description: String = "설명",
        stockQuantity: Int = 100,
    ): RegisterProductCommand = RegisterProductCommand(name, price, description, stockQuantity)
}