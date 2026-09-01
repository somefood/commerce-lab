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
            require(rawPassword.length >= PASSWORD_MIN_LENGTH) { "비밀번호는 ${PASSWORD_MIN_LENGTH}자 이상이어야 합니다." }
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

    fun verifyPassword(rawPassword: String, hasher: PasswordHasher): Boolean =
        hasher.verify(rawPassword, hashedPassword.value)

    // 생성자가 막혀 있으므로 상태 전이는 도메인 메서드로만 가능하다 (copy는 @ConsistentCopyVisibility로 봉인됨)
    fun promoteToAdmin(): Member = copy(role = Role.ADMIN)
}

@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        fun of(raw: String): Email {
            val normalized = raw.lowercase()
            require(normalized.contains("@")) { "이메일 형식이 아닙니다: $raw" }
            return Email(normalized)
        }
    }
}

@JvmInline
value class HashedPassword(val value: String)