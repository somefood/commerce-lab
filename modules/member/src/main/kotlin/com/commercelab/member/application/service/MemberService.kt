package com.commercelab.member.application.service

import com.commercelab.member.application.port.`in`.GetMemberQuery
import com.commercelab.member.application.port.`in`.RegisterMemberCommand
import com.commercelab.member.application.port.`in`.RegisterMemberUseCase
import com.commercelab.member.application.port.out.MemberRepository
import com.commercelab.member.domain.PasswordHasher
import com.commercelab.member.domain.DuplicateEmailException
import com.commercelab.member.domain.Email
import com.commercelab.member.domain.Member
import com.commercelab.member.domain.HashedPassword
import com.commercelab.member.domain.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordHasher: PasswordHasher
) : RegisterMemberUseCase, GetMemberQuery {

    @Transactional
    override fun registerCustomerMember(registerMemberCommand: RegisterMemberCommand): Member {
        val findMember = memberRepository.findByEmail(Email.of(registerMemberCommand.email))
        if (findMember != null) throw DuplicateEmailException()

        return memberRepository.save(
            Member.register(
                Email.of(registerMemberCommand.email),
                registerMemberCommand.password,
                passwordHasher,
                registerMemberCommand.name,
            )
        )
    }

    override fun getMember(id: Long): Member {
        return memberRepository.findById(id) ?: throw NoSuchElementException("Member not found")
    }
}