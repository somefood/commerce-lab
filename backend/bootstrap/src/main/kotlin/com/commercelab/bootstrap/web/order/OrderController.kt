package com.commercelab.bootstrap.web.order

import com.commercelab.order.api.OrderPlacement
import com.commercelab.order.api.PlaceOrder
import com.commercelab.order.api.PlaceOrderCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import java.net.URI
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
     * 실패 응답이 이 메서드에 안 보이는 것이 이번 전환의 요점이다.
     * OrderException은 잡지 않고 그대로 통과시킨다. ProblemDetailAdvice가 받아
     * OrderProblems.kt의 표대로 4xx로 바꾼다. 여기서 catch하면 그 표가 두 곳으로 갈라진다.
     *
     * 반환 타입이 ResponseEntity<PlaceOrder>로 좁혀졌다. 이전에는 성공/실패가 한 메서드에서
     * 나가느라 ResponseEntity<Any>였고, 그래서 springdoc이 성공 스키마조차 추론하지 못했다.
     *
     * @ApiResponse에 201만 적은 이유 — 실측 중이다:
     * springdoc은 @RestControllerAdvice 핸들러에 붙은 @ApiResponse를 전역으로 적용한다고 알려져 있다.
     * 사실이라면 400/404/409는 어드바이스에 한 번 적은 것으로 모든 엔드포인트에 붙는다.
     * 그래서 여기에는 일부러 적지 않았다. 확인 방법:
     *   curl -s localhost:8080/v3/api-docs | jq '.paths."/api/orders".post.responses | keys'
     *   → ["201","400","404","409"] 가 나오면 전역 적용이 사실이다
     *   → ["201"] 만 나오면 사실이 아니다. 그때는 이 메서드에 400/404/409를 도로 적어야 한다
     * 201을 손으로 적는 것은 어느 쪽이든 필요하다. springdoc은 반환 "타입"만 보고
     * 메서드 안의 .created(...)를 모르기 때문에 그냥 두면 200으로 적힌다.
     */
    @Operation(summary = "주문 생성")
    @ApiResponse(responseCode = "201", description = "주문 생성됨")
    @PostMapping("/api/orders")
    fun place(@RequestBody command: PlaceOrderCommand): ResponseEntity<PlaceOrder> {
        val order = orderPlacement.place(command)

        // 201 Created + Location. 본문도 함께 준다 —
        // 프론트가 orderId를 쓰려고 Location을 파싱하게 만들 이유가 없다.
        return ResponseEntity.created(URI("/api/orders/${order.orderId}")).body(order)
    }

    @GetMapping("/api/orders/{orderId}")
    fun getOrder(@PathVariable orderId: String): ResponseEntity<PlaceOrder> {
        TODO("OrderQuery 포트 구현 후 연결한다")
    }

    @PostMapping("/api/orders/{orderId}/confirm")
    fun confirmOrder(@PathVariable orderId: String): ResponseEntity<PlaceOrder> {
        TODO("3단계 선점 확정에서 연결한다")
    }
}
