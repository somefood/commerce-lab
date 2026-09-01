package com.commercelab.config

import com.commercelab.member.application.port.out.MemberRepository
import com.commercelab.member.domain.Email
import com.commercelab.member.domain.Member
import com.commercelab.member.domain.PasswordHasher
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 기동 시 ADMIN 계정 하나를 보장한다.
 *
 * apps/api에 있는 이유: "초기 데이터를 어떻게 심을 것인가"는 회원 도메인의 규칙이 아니라
 * 이 애플리케이션의 운영/조립 관심사다. (JwtTokenIssuer가 여기 있는 것과 같은 판단)
 */
@Component
class AdminAccountSeeder(
    private val memberRepository: MemberRepository,
    private val passwordHasher: PasswordHasher,
    @Value("\${app.admin.email}") private val adminEmail: String,
    @Value("\${app.admin.password}") private val adminPassword: String,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val email = Email.of(adminEmail)

        // 멱등: 이미 있으면 아무것도 하지 않는다. 재기동할 때마다 실행되기 때문.
        if (memberRepository.findByEmail(email) != null) return

        // register를 거치므로 비밀번호 길이 검증·해싱이 일반 가입과 동일하게 적용된다
        val admin = Member.register(email, adminPassword, passwordHasher, "관리자")
            .promoteToAdmin()

        memberRepository.save(admin)
    }
}
