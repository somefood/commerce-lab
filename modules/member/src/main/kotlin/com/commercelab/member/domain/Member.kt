package com.commercelab.member.domain

data class Member(
    val email: Email,
    val password: Password,
    val name: String
)

@JvmInline
value class Email(val value: String) {
    init {
        require(value.contains("@")) { "이메일 형식이 아닙니다: $value" }
    }
}

@JvmInline
value class Password(val value: String) {
    init {
        require(value.length >= 8) { "비밀번호는 8자 이상이어야 합니다: ${value.length}"}
    }
}