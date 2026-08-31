package com.commercelab.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize(HttpMethod.POST, "/api/members", permitAll)
                authorize(HttpMethod.GET, "/api/products/**", permitAll)
                authorize(HttpMethod.POST, "/api/products/**", hasRole("ADMIN"))
                authorize(HttpMethod.PUT, "/api/products/**", hasRole("ADMIN"))
                authorize(HttpMethod.DELETE, "/api/products/**", hasRole("ADMIN"))
                authorize(anyRequest, authenticated)
            }
        }
        return http.build()
    }
}