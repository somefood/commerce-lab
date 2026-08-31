package com.commercelab.member.application.service

import com.commercelab.member.application.port.out.MemberRepository
import com.commercelab.member.application.port.out.Token
import com.commercelab.member.application.port.out.TokenIssuer
import com.commercelab.member.domain.Email
import com.commercelab.member.domain.InvalidAuthenticationException
import com.commercelab.member.domain.PasswordHasher
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val memberRepository: MemberRepository,
    private val tokenIssuer: TokenIssuer,
    private val passwordHasher: PasswordHasher
) {
    fun authenticate(email: String, password: String): Token {
        val member = memberRepository.findByEmail(Email.of(email)) ?: throw InvalidAuthenticationException()
        if (!member.verifyPassword(password, passwordHasher)) {
            throw InvalidAuthenticationException()
        }
        return tokenIssuer.issue(member)
    }
}