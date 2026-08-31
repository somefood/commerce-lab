package com.commercelab.member.application.port.out

import com.commercelab.member.domain.Member

interface TokenIssuer {
    fun issue(member: Member): Token
}

@ConsistentCopyVisibility
data class Token private constructor(
    val value: String,
    val expiresIn: Long,
) {
    companion object {
        fun of(
            value: String,
            expiresIn: Long
        ) = Token(value, expiresIn)
    }
}