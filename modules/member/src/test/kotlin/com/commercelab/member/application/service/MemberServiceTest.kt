package com.commercelab.member.application.service

import com.commercelab.member.application.port.`in`.RegisterMemberCommand
import com.commercelab.member.application.port.out.MemberRepository
import com.commercelab.member.domain.PasswordHasher
import com.commercelab.member.domain.DuplicateEmailException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

class MemberServiceTest {

    private lateinit var memberRepository: MemberRepository
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var memberService: MemberService

    @BeforeEach
    fun setUp() {
        memberRepository = FakeMemberRepository()
        passwordHasher = FakePasswordHasher()
        memberService = MemberService(memberRepository, passwordHasher)
    }

    @Test
    fun `멤버를 등록한다`() {
        val member = memberService.registerCustomerMember(RegisterMemberCommand("a@a.com", "12345678", "주서콩"))

        assertThat(member.id).isNotNull
    }

    @Test
    fun `이미 등록된 이메일이 있으면 등록할 수 없다`() {
        memberService.registerCustomerMember(RegisterMemberCommand("a@a.com", "12345678", "주서콩"))

        assertThatThrownBy { memberService.registerCustomerMember(RegisterMemberCommand("a@a.com", "12345678", "주서콩")) }
            .isInstanceOf(DuplicateEmailException::class.java)
    }
}