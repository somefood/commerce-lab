package com.commercelab.member.adapter.out.security

import com.commercelab.member.domain.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHasher : PasswordHasher {

    private val bcryptEncoder = BCryptPasswordEncoder()

    override fun hash(password: String): String {
        return requireNotNull(bcryptEncoder.encode(password))
    }
}