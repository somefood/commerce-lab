package com.commercelab.member.application.port.`in`

import com.commercelab.member.domain.Email
import com.commercelab.member.domain.Member
import com.commercelab.member.domain.Password
import com.commercelab.member.domain.Role

interface RegisterMemberUseCase {

    fun registerCustomerMember(registerMemberCommand: RegisterMemberCommand): Member
}

data class RegisterMemberCommand(
    val email: String,
    val password: String,
    val name: String,
) {
    fun toCustomerMember(): Member = Member(
        null,
        Email(email),
        Password(password),
        name,
        Role.CUSTOMER
    )
}