package com.commercelab.product.domain

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ProductTest {

    @Test
    fun `가격이 음수면 상품을 생성할 수 없다`() {
        assertThatThrownBy {
            ProductFixture.product(price = -1L)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("가격은 0 이상")
    }

    @Test
    fun `재고가 음수면 상품을 생성할 수 없다`() {
        assertThatThrownBy { ProductFixture.product(stockQuantity = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}