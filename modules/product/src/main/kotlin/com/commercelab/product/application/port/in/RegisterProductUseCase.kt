package com.commercelab.product.application.port.`in`

import com.commercelab.product.adapter.`in`.web.ProductCreateRequest
import com.commercelab.product.domain.Product

interface RegisterProductUseCase {

    fun registerProduct(createRequest: ProductCreateRequest): Product
}