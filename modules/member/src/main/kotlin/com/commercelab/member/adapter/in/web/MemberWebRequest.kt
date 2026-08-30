package com.commercelab.member.adapter.`in`.web

import com.commercelab.member.application.port.`in`.RegisterMemberCommand
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class MemberRegisterRequest(
    @field:Email("이메일 형식이 올바르지 않습니다.")
    val email: String,

    @field:Size(min = 8)
    val password: String,
    val name: String
) {
    fun toRegisterMemberCommand(): RegisterMemberCommand {
        return RegisterMemberCommand(email, password, name)
    }
}