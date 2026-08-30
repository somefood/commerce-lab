package com.commercelab.member.domain

@ConsistentCopyVisibility
data class Member private constructor(
    val id: Long?,
    val email: Email,
    val hashedPassword: HashedPassword,
    val name: String,
    val role: Role
) {
    companion object {
        const val PASSWORD_MIN_LENGTH = 8

        fun register(email: Email, rawPassword: String, hasher: PasswordHasher, name: String): Member {
            require(rawPassword.length >= PASSWORD_MIN_LENGTH) { "비밀번호는 8자 이상이어야 합니다." }
            return Member(
                null,
                email,
                HashedPassword(hasher.hash(rawPassword)),
                name,
                Role.CUSTOMER
            )
        }

        // 저장소 전용: 이미 검증된 상태를 그대로 되살린다. 비즈니스 규칙을 다시 적용하지 않는다.
        fun reconstitute(id: Long, email: Email, hashedPassword: HashedPassword, name: String, role: Role): Member =
            Member(id, email, hashedPassword, name, role)
    }
}

@JvmInline
value class Email(val value: String) {
    init {
        require(value.contains("@")) { "이메일 형식이 아닙니다: $value" }
    }
}

@JvmInline
value class HashedPassword(val value: String)