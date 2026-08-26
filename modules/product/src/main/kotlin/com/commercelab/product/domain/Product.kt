package com.commercelab.product.domain

data class Product(
    val id: Long?,
    val name: String,
    val price: Long,
    val description: String,
    val stockQuantity: Int,
    val status: ProductStatus
) {
    init {
        require(price >= 0) { "가격은 0 이상이어야 합니다. price=$price" }
        require(stockQuantity >= 0) { "재고 수량은 0 이상이어야 합니다. stockQuantity=$stockQuantity" }
    }

    companion object {
        fun create(
            name: String,
            price: Long,
            description: String,
            stockQuantity: Int
        ): Product =
            Product(null, name, price, description, stockQuantity, ProductStatus.ACTIVE)
    }

    fun deactivate(): Product = copy(status = ProductStatus.INACTIVE)
}

enum class ProductStatus { ACTIVE, INACTIVE }