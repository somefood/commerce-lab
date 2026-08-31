package com.commercelab.security

import com.commercelab.member.application.port.out.Token
import com.commercelab.member.application.port.out.TokenIssuer
import com.commercelab.member.domain.Member
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit


@Component
class JwtTokenIssuer(
    private val jwtEncoder: JwtEncoder
) : TokenIssuer {
    companion object {
        private const val EXPIRES_IN_SECONDS = 3600L
    }

    override fun issue(member: Member): Token {
        val now = Instant.now()

        val claims = JwtClaimsSet.builder()
            .subject(requireNotNull(member.id).toString())      // sub
            .claim("email", member.email.value)
            .claim("role", member.role.name)    // enum은 .name으로
            .issuedAt(now)                      // iat
            .expiresAt(now.plusSeconds(EXPIRES_IN_SECONDS))  // exp
            .build()

        val header = JwsHeader.with(MacAlgorithm.HS256).build()

        return Token.of(
            jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue,
            EXPIRES_IN_SECONDS
        )
    }
}