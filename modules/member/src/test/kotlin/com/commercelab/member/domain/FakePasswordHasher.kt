package com.commercelab.member.domain

class FakePasswordHasher : PasswordHasher {
    override fun hash(password: String): String {
        return "hashed:$password"
    }

    override fun verify(password: String, hashedPassword: String): Boolean {
        return hashedPassword == hash(password)
    }
}