package com.commercelab.common

/**
 * 금액. 원 단위 정수로만 다룬다.
 *
 * Double을 쓰지 않는 이유: 부동소수점 오차가 원장 합계 검증(차변합 = 대변합)을
 * 깨뜨린다. 금액은 언제나 정수로 저장하고 표시할 때만 포맷한다.
 */
@JvmInline
value class Money private constructor(val amount: Long) {

    operator fun plus(other: Money): Money = Money(amount + other.amount)

    operator fun times(quantity: Int): Money = Money(amount * quantity)

    companion object {
        val ZERO: Money = Money(0)

        fun of(amount: Long): Money {
            require(amount >= 0) { "금액은 음수일 수 없습니다: $amount" }
            return Money(amount)
        }
    }
}
