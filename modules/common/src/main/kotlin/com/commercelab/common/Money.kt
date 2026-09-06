package com.commercelab.common

@JvmInline
value class Money private constructor(
    val amount: Long
) {
    init {
        require(amount >= 0) { "가격은 0 이상이어야 합니다. amount=$amount" }
    }

    companion object {
        val ZERO = Money(0)

        fun of(amount: Long) = Money(amount)
    }

    fun plus(money: Money) = Money(amount + money.amount)

    fun minus(money: Money) = Money(amount - money.amount)

    fun times(times: Int) = Money(amount * times)
}