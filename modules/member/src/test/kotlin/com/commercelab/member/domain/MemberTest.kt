package com.commercelab.member.domain

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class MemberTest {

    @Test
    fun `비밀번호는 8자 이상이어야 한다`() {
        val shortPassword = "1234567"


        Assertions.assertThatThrownBy {
            Member(
                Email("a@a.com"),
                Password(shortPassword),
                "주서콩"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}