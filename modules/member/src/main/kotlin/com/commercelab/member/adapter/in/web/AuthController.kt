package com.commercelab.member.adapter.`in`.web

import com.commercelab.member.application.service.AuthService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/api/auth/login")
    fun login(@Valid @RequestBody authRequest: AuthRequest): AuthResponse {
        val token = authService.authenticate(authRequest.email, authRequest.password)
        return AuthResponse(token.value, "Bearer", token.expiresIn)
    }
}