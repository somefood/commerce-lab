package com.commercelab.product.application.service

import com.commercelab.common.Money
import com.commercelab.product.application.port.`in`.EditProductCommand
import com.commercelab.product.domain.ProductFixture
import com.commercelab.product.domain.ProductStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest

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
        val registerProduct = productService.registerProduct(ProductFixture.createRequestCommand())

        assertThat(registerProduct.id).isNotNull
    }

    @Test
    fun `상품을 수정한다`() {
        val registerProduct = productService.registerProduct(ProductFixture.createRequestCommand())

        val editProductCommand = EditProductCommand(
            name = "수정상품1",
            price = 2000,
            description = "상품1 설명",
        )

        productService.editProduct(registerProduct.id!!, editProductCommand)

        val product = productService.getProduct(registerProduct.id)

        assertThat(product.name).isEqualTo("수정상품1")
        assertThat(product.price).isEqualTo(Money.of(2000))
    }

    @Test
    fun `상품 재고를 변경한다`() {
        val registerProduct = productService.registerProduct(ProductFixture.createRequestCommand())

        productService.adjustStock(registerProduct.id!!, 100)

        val product = productService.getProduct(registerProduct.id)
        assertThat(product.stockQuantity).isEqualTo(200)
    }

    @Test
    fun `상품을 삭제하면 판매중지 상태가 된다`() {
        val registerProduct = productService.registerProduct(ProductFixture.createRequestCommand())

        productService.deleteProduct(registerProduct.id!!)

        val product = productService.getProduct(registerProduct.id)
        assertThat(product.status).isEqualTo(ProductStatus.INACTIVE)
    }
}