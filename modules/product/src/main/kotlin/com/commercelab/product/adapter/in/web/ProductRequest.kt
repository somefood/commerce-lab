package com.commercelab.product.adapter.`in`.web

data class ProductCreateRequest(
    val name: String,
    val price: Long,
    val description: String,
    val stockQuantity: Int
)

data class ProductEditRequest(
    val name: String,
    val price: Long,
    val description: String,
    val stockQuantity: Int
)

