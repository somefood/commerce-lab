package com.commercelab.member.application.port.out

import com.commercelab.member.domain.Member

interface TokenIssuer {
    fun issue(member: Member): Token
}

@ConsistentCopyVisibility
data class Token private constructor(val value: String) {
    companion object {
        fun of(value: String) = Token(value)
    }
}