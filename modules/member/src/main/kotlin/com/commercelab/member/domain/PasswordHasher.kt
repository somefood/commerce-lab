package com.commercelab.member.domain

interface PasswordHasher {

    fun hash(password: String): String
}