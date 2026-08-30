package com.commercelab.member.adapter.`in`.web

import com.commercelab.member.domain.Member

data class MemberCreateResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: String
) {
    companion object {
        fun from(member: Member) = MemberCreateResponse(
            id = requireNotNull(member.id),
            email = member.email.value,
            name = member.name,
            role = member.role.name
        )
    }
}