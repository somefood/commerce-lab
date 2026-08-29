package com.commercelab.product.application.port.`in`


interface EditProductUseCase {

    fun editProduct(
        id: Long,
        editRequest: EditProductCommand
    )
}

data class EditProductCommand(
    val name: String,
    val price: Long,
    val description: String?,
    val stockQuantity: Int
)