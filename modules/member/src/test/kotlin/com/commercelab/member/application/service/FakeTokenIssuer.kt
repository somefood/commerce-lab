package com.commercelab.member.application.service

import com.commercelab.member.application.port.out.Token
import com.commercelab.member.application.port.out.TokenIssuer
import com.commercelab.member.domain.Member

class FakeTokenIssuer : TokenIssuer {

    override fun issue(member: Member): Token {
        return Token.of("token")
    }
}