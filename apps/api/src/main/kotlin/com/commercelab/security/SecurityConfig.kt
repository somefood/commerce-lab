package com.commercelab.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.StandardCharset
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
class SecurityConfig(
    @Value("\${JWT_SECRET}")
    private val secretKey: String
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            oauth2ResourceServer { jwt {} }
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

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val sk: SecretKey = SecretKeySpec(secretKey.toByteArray(StandardCharset.UTF_8), MacAlgorithm.HS256.getName())
        return NimbusJwtDecoder.withSecretKey(sk).build()
    }

    @Bean
    fun jwtEncoder(): JwtEncoder {
        val jwk: JWK = OctetSequenceKey.Builder(secretKey.toByteArray(StandardCharset.UTF_8))
            .algorithm(JWSAlgorithm.HS256)
            .build()
        val jwkSource: JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(jwk))

        return NimbusJwtEncoder(jwkSource)
    }
}
