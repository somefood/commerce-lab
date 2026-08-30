package com.commercelab.member.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface MemberJpaRepository : JpaRepository<MemberJpaEntity, Long> {

    fun findByEmail(email: String): Optional<MemberJpaEntity>
}