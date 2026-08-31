package com.commercelab.member.adapter.`in`.web

import jakarta.validation.constraints.NotBlank

data class AuthRequest(
    @field:NotBlank
    val email: String,
    @field:NotBlank
    val password: String
)