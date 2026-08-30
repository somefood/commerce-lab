package com.commercelab.member.application.port.service

import com.commercelab.member.application.port.`in`.RegisterMemberCommand
import com.commercelab.member.application.port.`in`.RegisterMemberUseCase
import com.commercelab.member.application.port.out.MemberRepository
import com.commercelab.member.domain.Member
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class MemberService(
    private val memberRepository: MemberRepository
) : RegisterMemberUseCase {

    @Transactional
    override fun registerMember(registerMemberCommand: RegisterMemberCommand): Member {
        return memberRepository.save(registerMemberCommand.toCustomerMember())
    }
}