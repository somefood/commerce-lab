package com.commercelab.bootstrap.web.order

import com.commercelab.order.api.OrderException
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 예상된 비즈니스 실패([OrderException])를 HTTP 응답으로 옮긴다.
 * 상태 코드와 본문은 [toProblemDetail]의 표가 정한다.
 *
 * ## 왜 ProblemDetailAdvice에서 떼어냈나 (2026-08-25)
 *
 * springdoc은 `@RestControllerAdvice` 핸들러의 `@ApiResponse`를 **그 어드바이스가 적용되는
 * 모든 컨트롤러에** 붙인다. 전역 어드바이스에 두었더니 관계없는 곳까지 오염됐다.
 *
 * ```
 * POST /api/dev/reset  ['200','400','404','409']   ← 재고 초기화가 409를 낼 수 있나
 * GET  /api/health     ['200','400','404','409']   ← 헬스체크가 404를 낼 수 있나
 * ```
 *
 * 명세는 클라이언트가 믿고 코드를 짜는 계약이다. 있지도 않은 응답이 적혀 있으면
 * 클라이언트는 오지 않을 분기를 짜고, 그 분기는 영원히 검증되지 않는다.
 *
 * `assignableTypes`로 적용 대상을 [OrderController]로 좁혔다.
 * 런타임 동작과 명세가 **같은 선언 하나**로 정해지는 것이 핵심이다 —
 * 컨트롤러마다 `@ApiResponses`를 손으로 적으면 분기를 추가하고 애노테이션을 빠뜨렸을 때
 * 명세가 조용히 거짓말을 한다.
 *
 * ## @Order가 필요한 이유
 *
 * [com.commercelab.bootstrap.web.ProblemDetailAdvice]의 `@ExceptionHandler(Exception::class)`도
 * `OrderException`에 매칭된다. 스프링은 **어드바이스 빈을 @Order 순서로 훑어**
 * 먼저 걸리는 것을 쓴다. 순서를 지정하지 않으면 전역 어드바이스가 먼저 잡아
 * 재고 부족이 500으로 나갈 수 있다. (같은 클래스 안에서는 더 구체적인 것을 고르지만,
 * 클래스가 다르면 그 규칙이 적용되지 않는다.)
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [OrderController::class])
class OrderExceptionAdvice {

    /**
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
     * `@ResponseStatus`를 붙이면 오히려 고정돼서 이 표가 무력화된다.
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
}
