package com.commercelab.member.domain

class FakePasswordHasher : PasswordHasher {
    override fun hash(password: String): String {
        return "hashed:$password"
    }
}