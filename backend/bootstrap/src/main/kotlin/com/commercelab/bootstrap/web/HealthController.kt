package com.commercelab.bootstrap.web

import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * actuator의 /actuator/health와 별개로 두는 이유: actuator 응답 형식은
 * 스프링이 소유하므로 OpenAPI 계약에 넣기 부적절하다. 프론트가 의존할
 * 계약은 우리가 소유하는 엔드포인트여야 한다.
 */
@RestController
@RequestMapping("/api")
class HealthController {

    @Operation(summary = "서비스 생존 확인")
    @GetMapping("/health")
    fun health(): HealthResponse = HealthResponse(status = "UP", service = "commerce-lab")
}

data class HealthResponse(
    val status: String,
    val service: String,
)
