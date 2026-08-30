package com.commercelab.member.application.port.service

import com.commercelab.member.application.port.out.MemberRepository
import com.commercelab.member.domain.Email
import com.commercelab.member.domain.Member

class FakeMemberRepository : MemberRepository {

    private val store = LinkedHashMap<Long, Member>()
    private var sequence = 0L

    override fun save(member: Member): Member {
        val id = member.id ?: ++sequence
        val saved = member.copy(id = id)
        store[id] = saved
        return saved
    }

    override fun findByEmail(email: Email): Member? {
        return store.values.find { it.email == email }
    }
}