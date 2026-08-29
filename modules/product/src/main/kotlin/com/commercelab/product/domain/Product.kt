package com.commercelab.product.domain

data class Product(
    val id: Long?,
    val name: String,
    val price: Long,
    val description: String?,
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
            description: String?,
            stockQuantity: Int
        ): Product =
            Product(null, name, price, description, stockQuantity, ProductStatus.ACTIVE)
    }

    fun deactivate(): Product = copy(status = ProductStatus.INACTIVE)

    fun adjustStockQuantity(quantity: Int): Product {
        if (stockQuantity + quantity < 0) throw IllegalArgumentException("수량은 0 미만이 될 수 없습니다. 현재 수량=${stockQuantity}")
        return copy(stockQuantity = stockQuantity + quantity)
    }
}

enum class ProductStatus { ACTIVE, INACTIVE }