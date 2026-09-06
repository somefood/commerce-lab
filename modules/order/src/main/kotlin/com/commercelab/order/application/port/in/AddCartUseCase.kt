package com.commercelab.order.application.port.`in`

interface AddCartUseCase {

    fun addCart(memberId: Long, productId: Long, amount: Int)
}