package com.commercelab.product.application.port.`in`

import com.commercelab.product.domain.Product

interface RegisterProductUseCase {

    fun registerProduct(registerProductCommand: RegisterProductCommand): Product
}

data class RegisterProductCommand(
    val name: String,
    val price: Long,
    val description: String?,
    val stockQuantity: Int
)