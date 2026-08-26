package com.commercelab.product.application.service

import com.commercelab.product.adapter.`in`.web.ProductCreateRequest
import com.commercelab.product.application.port.out.ProductRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProductServiceTest {

    private lateinit var productRepository: FakeProductRepository
    private lateinit var productService: ProductService

    @BeforeEach
    fun setUp() {
        productRepository = FakeProductRepository()
        productService = ProductService(productRepository)
    }

    @Test
    fun `상품을 등록한다`() {
        val productCreateRequest = ProductCreateRequest(
            name = "상품1",
            price = 1000,
            description = "상품1 설명",
            stockQuantity = 10
        )

        val registerProduct = productService.registerProduct(productCreateRequest)

        Assertions.assertThat { registerProduct.id }.isNotNull
    }
}