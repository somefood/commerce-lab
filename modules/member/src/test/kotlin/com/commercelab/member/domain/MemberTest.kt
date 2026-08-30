package com.commercelab.member.domain

import com.commercelab.member.application.service.FakePasswordHasher
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class MemberTest {

    @Test
    fun `비밀번호는 8자 이상이어야 한다`() {
        val shortPassword = "1234567"


        Assertions.assertThatThrownBy {
            Member.register(
                Email("a@a.com"),
                shortPassword,
                FakePasswordHasher(),
                "주서콩",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}