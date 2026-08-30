package com.commercelab.member.adapter.`in`.web

data class MemberCreateResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: String
)