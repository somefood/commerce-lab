package com.commercelab.member.application.port.out

import com.commercelab.member.domain.Email
import com.commercelab.member.domain.Member

interface MemberRepository {
    fun save(member: Member): Member
    fun findById(id: Long): Member?
    fun findByEmail(email: Email): Member?
}