package com.commercelab.member.application.port.`in`

import com.commercelab.member.domain.Member

interface GetMemberQuery {

    fun getMember(id: Long): Member
}