package com.commercelab.member.adapter.`in`.web

import com.commercelab.member.application.port.`in`.GetMemberQuery
import com.commercelab.member.application.port.`in`.RegisterMemberUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.security.Principal

@RestController
class MemberController(
    private val registerMemberUseCase: RegisterMemberUseCase,
    private val getMemberQuery: GetMemberQuery
) {

    @PostMapping("/api/members")
    fun registerMember(@Valid @RequestBody registerRequest: MemberRegisterRequest): ResponseEntity<MemberDetailResponse> {
        val member = registerMemberUseCase.registerCustomerMember(registerRequest.toRegisterMemberCommand())
        return ResponseEntity
            .created(URI("/api/members/${member.id}"))
            .body(
                MemberDetailResponse.from(member)
            )
    }

    @GetMapping("/api/members/me")
    fun getMemberInfo(principal: Principal): ResponseEntity<MemberDetailResponse> {
        val memberId = principal.name.toLong()
        val member = getMemberQuery.getMember(memberId)
        return ResponseEntity.ok(MemberDetailResponse.from(member))
    }
}