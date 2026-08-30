package com.commercelab.member.application.service

import com.commercelab.member.application.port.out.PasswordHasher

class FakePasswordHasher : PasswordHasher {
    override fun hash(password: String): String {
        return "1234567890"
    }
}