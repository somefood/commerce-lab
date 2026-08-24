package com.commercelab.bootstrap.web

import com.commercelab.bootstrap.web.order.toProblemDetail
import com.commercelab.order.api.OrderException
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.servlet.http.HttpServletRequest
import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 예외를 RFC 9457 Problem Details 형식으로 바꾼다.
 *
 * 두 종류를 다룬다. **이 구분이 4xx와 5xx를 가르는 유일한 선이다.**
 *   - [OrderException] — 예상된 비즈니스 실패. 4xx로 나간다
 *   - 그 밖의 모든 것 — 예상하지 못한 사고. 500으로 나간다
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
     * 예상된 비즈니스 실패. 상태 코드와 본문은 [toProblemDetail]의 표가 정한다.
     *
     * 여기서 예외를 잡아도 트랜잭션은 이미 롤백된 뒤다.
     * `@Transactional` 프록시가 서비스 메서드를 빠져나오는 시점에 롤백을 결정하고,
     * 어드바이스는 그보다 훨씬 바깥(DispatcherServlet 밖)에서 돈다.
     * 순서가 반대였다면 실패 응답과 함께 데이터가 남았을 것이다.
     *
     * 로그를 남기지 않는다. 재고 부족은 초당 수백 번 일어나는 정상적인 결과다.
     * 그걸 error로 찍으면 진짜 사고가 로그에 파묻힌다.
     * 필요한 것은 로그가 아니라 메트릭이다(M5에서 다룬다).
     *
     * 반환 타입이 ProblemDetail인데 상태 코드가 어떻게 정해지나:
     * 스프링 6은 ProblemDetail을 반환하면 그 안의 status를 응답 상태로 쓴다.
     * @ResponseStatus를 붙이면 오히려 고정돼서 이 표가 무력화된다.
     */
    @ApiResponses(
        ApiResponse(
            responseCode = "400", description = "요청이 잘못됨 (수량 오류, 빈 주문)",
            content = [Content(schema = Schema(implementation = ProblemDetail::class))],
        ),
        ApiResponse(
            responseCode = "404", description = "상품 또는 주문 없음",
            content = [Content(schema = Schema(implementation = ProblemDetail::class))],
        ),
        ApiResponse(
            responseCode = "409", description = "재고 부족, 동시 요청 충돌, 상태 전이 불가",
            content = [Content(schema = Schema(implementation = ProblemDetail::class))],
        ),
    )
    @ExceptionHandler(OrderException::class)
    fun handleOrderException(ex: OrderException): ProblemDetail = ex.toProblemDetail()

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
     * 위의 OrderException 핸들러가 이 핸들러를 무력화하지 않는 이유:
     * 스프링은 예외 타입에 더 가까운(구체적인) 핸들러를 고른다.
     * OrderException은 여기 걸리지 않고 위로 간다. 그 외에는 전부 여기다 —
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
