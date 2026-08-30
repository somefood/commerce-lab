package com.commercelab.member.domain

import com.commercelab.member.application.service.FakePasswordHasher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MemberTest {

    @Test
    fun `비밀번호는 8자 이상이어야 한다`() {
        val shortPassword = "1234567"


        assertThatThrownBy {
            Member.register(
                Email.of("a@a.com"),
                shortPassword,
                FakePasswordHasher(),
                "주서콩",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `이메일은 소문자로 정규화된다`() {
        assertThat(Email.of(" A@Example.COM ").value).isEqualTo("a@example.com")
    }

    @Test
    fun `대소문자만 다른 이메일은 같은 이메일이다`() {
        assertThat(Email.of("A@a.com")).isEqualTo(Email.of("a@a.com"))
    }
}