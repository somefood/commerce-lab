package com.commercelab.product.application.port.`in`

interface AdjustStockUseCase {

    fun adjustStock(productId: Long, amount: Int)
}