package com.commercelab.product.application.port.`in`

import com.commercelab.product.adapter.`in`.web.ProductEditRequest

interface EditProductUseCase {

    fun editProduct(
        id: Long,
        editRequest: ProductEditRequest
    )
}