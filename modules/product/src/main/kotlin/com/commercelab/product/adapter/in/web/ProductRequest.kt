package com.commercelab.product.adapter.`in`.web

import com.commercelab.product.application.port.`in`.EditProductCommand
import com.commercelab.product.application.port.`in`.RegisterProductCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class ProductCreateRequest(
    @field:NotBlank
    @field:Size(max = 100, message = "상품명은 100자 이하여야 합니다")
    val name: String,

    @field:PositiveOrZero
    val price: Long,

    val description: String?,

    @field:PositiveOrZero
    val stockQuantity: Int
) {
    fun toRegisterProductCommand(): RegisterProductCommand {
        return RegisterProductCommand(name, price, description, stockQuantity)
    }
}

data class ProductEditRequest(
    val name: String,
    val price: Long,
    val description: String?,
    val stockQuantity: Int
) {
    fun toEditProductCommand(): EditProductCommand {
        return EditProductCommand(name, price, description, stockQuantity)
    }
}

