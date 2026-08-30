package com.commercelab.member.adapter.out.persistence

import com.commercelab.member.domain.Role
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class MemberJpaEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val email: String,
    val password: String,
    val name: String,

    @Enumerated(EnumType.STRING)
    val role: Role,
)