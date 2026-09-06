package com.commercelab.common

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `음수를 넣을 수 없다`() {
        assertFailsWith<IllegalArgumentException> { Money.of(-1) }
    }
}