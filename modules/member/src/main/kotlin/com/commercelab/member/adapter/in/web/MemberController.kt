package com.commercelab.member.adapter.`in`.web

import com.commercelab.member.application.port.`in`.RegisterMemberUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
class MemberController(
    private val registerMemberUseCase: RegisterMemberUseCase
) {

    @PostMapping("/api/members")
    fun registerMember(@Valid @RequestBody registerRequest: MemberRegisterRequest): ResponseEntity<MemberCreateResponse> {
        val member = registerMemberUseCase.registerCustomerMember(registerRequest.toRegisterMemberCommand())
        return ResponseEntity
            .created(URI("/api/members/${member.id}"))
            .body(
                MemberCreateResponse.from(member)
            )
    }
}