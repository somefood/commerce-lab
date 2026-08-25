package com.commercelab.bootstrap.web

import jakarta.servlet.http.HttpServletRequest
import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * **예상하지 못한** 예외를 RFC 9457 Problem Details 형식으로 바꾼다. 전역이다.
 *
 * 예상된 비즈니스 실패(`OrderException`)는 여기 없다.
 * [com.commercelab.bootstrap.web.order.OrderExceptionAdvice]가 order 컨트롤러에만 적용되도록
 * 범위를 좁혀서 따로 처리한다 — 전역에 두었더니 헬스체크 명세에까지 409가 붙었다(2026-08-25).
 *
 * **이 클래스와 저 클래스의 경계가 4xx와 5xx를 가르는 유일한 선이다.**
 *   - `OrderException`을 상속한다 → 예상된 실패 → 4xx
 *   - 상속하지 않는다 → 사고 → 여기 → 500
 *
 * ResponseEntityExceptionHandler를 상속하는 이유:
 * 스프링 MVC가 스스로 던지는 예외들(본문 파싱 실패, 지원하지 않는 메서드, 검증 실패 등)을
 * 부모 클래스가 이미 적절한 상태 코드와 ProblemDetail로 변환한다.
 * 직접 잡으면 그 목록을 손으로 관리해야 하고, 스프링이 새 예외를 추가할 때마다 뒤처진다.
 */
@RestControllerAdvice
class ProblemDetailAdvice : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 아무도 처리하지 않은 예외의 최후 방어선. 500으로 나간다.
     *
     * 500을 500으로 내보내는 것이 핵심이다.
     * 이전 버전은 모든 RuntimeException을 400으로 바꿨는데, 그러면
     * NullPointerException도 커넥션 풀 고갈도 전부 "클라이언트가 잘못 보냈다"가 된다.
     * 서버 장애가 4xx로 위장되면 알림이 울리지 않고, 대시보드의 5xx 그래프는 평평하다.
     * 무엇보다 `서버오류 == 0`을 검사하는 통합 테스트가 영원히 통과한다 —
     * 계측기가 고장 난 채로 초록불이 켜진다.
     *
     * OrderException이 여기 걸리지 않는 이유:
     * OrderExceptionAdvice가 @Order(HIGHEST_PRECEDENCE)라 먼저 훑힌다.
     * 스프링은 어드바이스 빈을 @Order 순서로 보고 먼저 걸리는 것을 쓴다 —
     * 클래스가 다르면 "더 구체적인 타입" 규칙은 적용되지 않는다. 그 외에는 전부 여기다 —
     * Inventory.addReserved의 음수 검사(IllegalArgumentException)가 그 예다.
     * 그건 "일어나면 버그"이므로 500이 맞다.
     *
     * detail에 ex.message를 넣지 않는 이유:
     * 예외 메시지에는 테이블명, 쿼리, 파일 경로가 섞여 나온다. 공격자에게 지도를 주는 셈이다.
     * 클라이언트에게는 "실패했다"만 알리고, 원인은 로그에 남긴다.
     * 둘을 잇는 것은 추적 ID의 몫이다(M5 관측성에서 다룬다).
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ProblemDetail {
        // ex를 마지막 인자로 넘기면 SLF4J가 스택트레이스를 함께 찍는다.
        // 여기서 로그를 빠뜨리면 500의 원인을 영영 알 수 없다.
        log.error("처리되지 않은 예외: {} {}", request.method, request.requestURI, ex)

        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            type = URI.create("https://commerce-lab.dev/problems/internal-error")
            title = "Internal Server Error"
            detail = "요청을 처리하지 못했습니다."
        }
    }
}
