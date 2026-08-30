package com.commercelab.member.application.port.`in`

import com.commercelab.member.domain.Member

interface RegisterMemberUseCase {

    fun registerCustomerMember(registerMemberCommand: RegisterMemberCommand): Member
}

data class RegisterMemberCommand(
    val email: String,
    val password: String,
    val name: String,
)