package com.commercelab.order.domain

@ConsistentCopyVisibility
data class Cart private constructor(
    val id: Long? = null,
    val memberId: Long,
    val items: List<CartItem> = emptyList()
) {
    companion object {
        fun empty(memberId: Long) = Cart(memberId = memberId)
        fun of(id: Long, memberId: Long, items: List<CartItem>) = Cart(id, memberId, items)
    }
}

@ConsistentCopyVisibility
data class CartItem private constructor(
    val cart: Cart,
    val productId: Long,
    val quantity: Int
) {
    init {
        require(quantity >= 0) {
            "음수는 입력할 수 없음 quantity=$quantity"
        }
    }
}
