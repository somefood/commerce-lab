package com.commercelab.bootstrap.web.order

import com.commercelab.common.fold
import com.commercelab.order.api.OrderPlacement
import com.commercelab.order.api.PlaceOrder
import com.commercelab.order.api.PlaceOrderCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import java.net.URI
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class OrderController(
    private val orderPlacement: OrderPlacement,
) {

    /**
     * 반환 타입이 ResponseEntity<Any>인 이유:
     * 성공은 PlaceOrder, 실패는 ProblemDetail이라 하나의 타입으로 좁힐 수 없다.
     *
     * 그러면 springdoc이 스키마를 추론하지 못하므로 @ApiResponses로 직접 적는다.
     * 사실 추론에 맡겨도 이 엔드포인트의 명세는 틀린다 — springdoc은 반환 "타입"만 보고
     * 메서드 안의 상태 코드를 모르기 때문에, ResponseEntity<PlaceOrder>로 두면
     * 201을 반환하는데도 명세에는 200으로 적힌다. 실패 응답은 아예 나오지 않는다.
     * 어차피 손으로 적어야 한다면 정확하게 적는 편이 낫다.
     *
     * 대가: when에 분기를 추가하고 여기 @ApiResponse를 안 늘리면 명세가 거짓말을 한다.
     *       컴파일러가 잡아주지 않는다. when의 exhaustive 검사와 달리 사람이 지켜야 한다.
     */
    @Operation(summary = "주문 생성")
    @ApiResponses(
        ApiResponse(
            responseCode = "201", description = "주문 생성됨",
            content = [Content(schema = Schema(implementation = PlaceOrder::class))],
        ),
        ApiResponse(
            responseCode = "400", description = "요청이 잘못됨 (수량 오류, 빈 주문)",
            content = [Content(schema = Schema(implementation = ProblemDetail::class))],
        ),
        ApiResponse(
            responseCode = "404", description = "상품 없음",
            content = [Content(schema = Schema(implementation = ProblemDetail::class))],
        ),
        ApiResponse(
            responseCode = "409", description = "재고 부족 또는 동시 요청 충돌",
            content = [Content(schema = Schema(implementation = ProblemDetail::class))],
        ),
    )
    @PostMapping("/api/orders")
    fun place(@RequestBody command: PlaceOrderCommand): ResponseEntity<Any> =
        orderPlacement.place(command).fold(
            onSuccess = { order ->
                // 201 Created + Location. 본문도 함께 준다 —
                // 프론트가 orderId를 쓰려고 Location을 파싱하게 만들 이유가 없다.
                ResponseEntity.created(URI("/api/orders/${order.orderId}")).body(order)
            },
            // 실패를 예외로 바꾸지 않는다. OrderError를 그대로 응답으로 옮긴다.
            // 표는 OrderProblems.kt에 있다.
            onFailure = { error -> error.toResponse() },
        )

    @GetMapping("/api/orders/{orderId}")
    fun getOrder(@PathVariable orderId: String): ResponseEntity<Any> {
        TODO("OrderQuery 포트 구현 후 연결한다")
    }

    @PostMapping("/api/orders/{orderId}/confirm")
    fun confirmOrder(@PathVariable orderId: String): ResponseEntity<Any> {
        TODO("3단계 선점 확정에서 연결한다")
    }
}
