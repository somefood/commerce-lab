package com.commercelab.member.adapter.out.security

import com.commercelab.member.application.port.out.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHasher : PasswordHasher {

    override fun hash(password: String): String {
        val encode = BCryptPasswordEncoder().encode(password)
        return encode!!
    }
}