package com.commercelab.order.application.service

import com.commercelab.order.application.port.`in`.AddCartUseCase
import com.commercelab.order.application.port.out.CartRepository
import com.commercelab.order.application.port.out.ProductCatalog
import com.commercelab.order.domain.Cart
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional
@Service
class CartService(
    private val cartRepository: CartRepository,
    private val productCatalog: ProductCatalog
) : AddCartUseCase {

    override fun addCart(memberId: Long, productId: Long, amount: Int) {
        val cart = cartRepository.findByMemberId(memberId) ?: Cart.empty(memberId)

    }
}