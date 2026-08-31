package com.commercelab.member.adapter.`in`.web

data class AuthResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)