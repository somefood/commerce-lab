package com.commercelab.member.adapter.out.persistence

import com.commercelab.member.domain.Role
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "members")
class MemberJpaEntity(
    @Column(unique = true, nullable = false)
    var email: String,
    var password: String,
    var name: String,

    @Enumerated(EnumType.STRING)
    var role: Role,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}