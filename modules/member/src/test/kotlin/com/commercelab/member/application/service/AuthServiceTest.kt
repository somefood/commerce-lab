package com.commercelab.member.application.service

import com.commercelab.member.application.port.`in`.RegisterMemberCommand
import com.commercelab.member.application.port.out.MemberRepository
import com.commercelab.member.application.port.out.TokenIssuer
import com.commercelab.member.domain.FakePasswordHasher
import com.commercelab.member.domain.InvalidAuthenticationException
import com.commercelab.member.domain.PasswordHasher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthServiceTest {

    private lateinit var authService: AuthService
    private lateinit var memberRepository: MemberRepository
    private lateinit var tokenIssuer: TokenIssuer
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var memberService: MemberService

    @BeforeEach
    fun setUp() {
        memberRepository = FakeMemberRepository()
        tokenIssuer = FakeTokenIssuer()
        passwordHasher = FakePasswordHasher()
        authService = AuthService(memberRepository, tokenIssuer, passwordHasher)
        memberService = MemberService(memberRepository, passwordHasher)
    }

    @Test
    fun `인증이 완료되면 토큰이 발급된다`() {
        val email = "a@a.com"
        val password = "12345678"

        val member =
            memberService.registerCustomerMember(RegisterMemberCommand(email, password, "주석"))

        val authenticate = authService.authenticate(email, "12345678")
        assertThat(tokenIssuer.issue(member)).isEqualTo(authenticate)
    }

    @Test
    fun `이메일 또는 비밀번호가 일치하지 않으면 예외가 발생한다`() {
        val email = "a@a.com"
        val password = "12345678"

        memberService.registerCustomerMember(RegisterMemberCommand(email, password, "주석"))

        assertThatThrownBy({
            authService.authenticate("invalid@a.com", "12345678")
        })
            .isInstanceOf(InvalidAuthenticationException::class.java)
            .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.")

        assertThatThrownBy({
            authService.authenticate(email, "1234567")
        })
            .isInstanceOf(InvalidAuthenticationException::class.java)
            .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.")
    }
}