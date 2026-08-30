package com.commercelab.member.application.port.service

import com.commercelab.member.application.port.`in`.RegisterMemberCommand
import com.commercelab.member.application.port.out.MemberRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

class MemberServiceTest {

    private lateinit var memberRepository: MemberRepository
    private lateinit var memberService: MemberService

    @BeforeEach
    fun setUp() {
        memberRepository = FakeMemberRepository()
        memberService = MemberService(memberRepository)
    }

    @Test
    fun `멤버를 등록한다`() {
        val member = memberService.registerMember(RegisterMemberCommand("a@a.com", "12345678", "주서콩"))

        Assertions.assertThat(member.id).isNotNull
    }
}