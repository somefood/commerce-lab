package com.commercelab.member.application.port.out

interface PasswordHasher {

    fun hash(password: String): String
}