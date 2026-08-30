package com.commercelab.member.application.port.service

import com.commercelab.member.application.port.`in`.RegisterMemberCommand
import com.commercelab.member.application.port.`in`.RegisterMemberUseCase
import com.commercelab.member.application.port.out.MemberRepository
import com.commercelab.member.application.port.out.PasswordHasher
import com.commercelab.member.domain.Email
import com.commercelab.member.domain.Member
import com.commercelab.member.domain.Password
import com.commercelab.member.domain.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordHasher: PasswordHasher
) : RegisterMemberUseCase {

    @Transactional
    override fun registerCustomerMember(registerMemberCommand: RegisterMemberCommand): Member {
        val findMember = memberRepository.findByEmail(Email(registerMemberCommand.email))
        if (findMember != null) throw EmailAlreadyExistException()

        return memberRepository.save(
            Member(
                null,
                Email(registerMemberCommand.email),
                Password(passwordHasher.hash(registerMemberCommand.password)),
                registerMemberCommand.name,
                Role.CUSTOMER
            )
        )
    }
}