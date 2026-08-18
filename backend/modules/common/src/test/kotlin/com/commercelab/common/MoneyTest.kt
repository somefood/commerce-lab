package com.commercelab.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `같은 통화 금액을 더한다`() {
        val result = Money.of(1_000) + Money.of(2_500)
        assertEquals(Money.of(3_500), result)
    }

    @Test
    fun `수량만큼 곱한다`() {
        val result = Money.of(1_200) * 3
        assertEquals(Money.of(3_600), result)
    }

    @Test
    fun `음수 금액은 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            Money.of(-1)
        }
    }
}
