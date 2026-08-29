package com.commercelab.product.adapter.`in`.web

import com.commercelab.product.domain.Product
import com.commercelab.product.domain.ProductStatus

data class ProductCreateResponse(
    val id: Long?,
    val name: String,
    val price: Long,
    val description: String?,
    val stockQuantity: Int,
    val status: ProductStatus
) {
    companion object {
        fun from(product: Product): ProductCreateResponse {
            return ProductCreateResponse(
                id = product.id,
                name = product.name,
                price = product.price,
                description = product.description,
                stockQuantity = product.stockQuantity,
                status = product.status
            )
        }
    }
}

data class ProductResponse(
    val id: Long?,
    val name: String,
    val price: Long,
    val description: String?,
    val stockQuantity: Int,
    val status: ProductStatus
) {
    companion object {
        fun from(product: Product): ProductResponse {
            return ProductResponse(
                id = product.id,
                name = product.name,
                price = product.price,
                description = product.description,
                stockQuantity = product.stockQuantity,
                status = product.status
            )
        }
    }
}

data class ProductListResponse(
    val list: List<ProductResponse>
) {
    companion object {
        fun from(products: List<Product>): ProductListResponse {
            val responses = products.map {
                ProductResponse(
                    id = it.id,
                    name = it.name,
                    price = it.price,
                    description = it.description,
                    stockQuantity = it.stockQuantity,
                    status = it.status
                )
            }
            return ProductListResponse(responses)
        }
    }
}