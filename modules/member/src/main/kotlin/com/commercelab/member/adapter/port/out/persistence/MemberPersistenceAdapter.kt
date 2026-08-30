package com.commercelab.member.adapter.port.out.persistence

import com.commercelab.member.application.port.out.MemberRepository
import com.commercelab.member.domain.Email
import com.commercelab.member.domain.Member
import com.commercelab.member.domain.Password
import org.springframework.stereotype.Repository
import kotlin.jvm.optionals.getOrNull

@Repository
class MemberPersistenceAdapter(
    private val memberJpaRepository: MemberJpaRepository
) : MemberRepository {

    override fun save(member: Member): Member {
        val jpaEntity = memberJpaRepository.save(member.toJpaEntity())
        return jpaEntity.toDomain()
    }

    override fun findById(id: Long): Member? {
        return memberJpaRepository.findById(id)
            .getOrNull()?.toDomain()
    }

    override fun findByEmail(email: Email): Member? {
        return memberJpaRepository.findByEmail(email.value)
            .getOrNull()?.toDomain()
    }
}

fun Member.toJpaEntity(): MemberJpaEntity {
    return MemberJpaEntity(
        id = id,
        email = email.value,
        password = password.value,
        name = name,
        role = role
    )
}

fun MemberJpaEntity.toDomain(): Member {
    return Member(
        id = id,
        email = Email(email),
        password = Password(password),
        name = name,
        role = role
    )
}