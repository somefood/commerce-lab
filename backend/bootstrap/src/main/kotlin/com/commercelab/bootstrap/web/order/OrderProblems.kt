package com.commercelab.bootstrap.web.order

import com.commercelab.order.api.OrderError
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity

/**
 * 도메인 실패(OrderError)를 HTTP 응답으로 옮기는 유일한 지점.
 *
 * 이 표가 컨트롤러 밖에 있는 이유:
 * place / getOrder / confirm 이 같은 OrderError를 공유한다. 컨트롤러마다 when을 반복하면
 * 같은 에러가 엔드포인트마다 다른 상태 코드로 나가는 사고가 조용히 생긴다.
 *
 * else를 쓰지 않는다:
 * OrderError는 sealed interface다. 케이스를 빠뜨리면 컴파일이 실패한다.
 * else를 넣는 순간 그 안전장치가 꺼지고, 새 에러가 추가돼도 아무도 알려주지 않는다.
 */
fun OrderError.toResponse(): ResponseEntity<Any> {
    val problem = when (this) {
        // 409 — 지금 이 자원의 상태와 충돌한다. 시간이 지나면 결과가 달라질 수 있다.
        //       재고는 반품·입고·선점 만료로 다시 생긴다. "다시 시도해볼 만하다"가 409의 뜻이다.
        is OrderError.OutOfStock -> problem(
            status = HttpStatus.CONFLICT,
            type = "out-of-stock",
            title = "재고 부족",
            detail = "요청 수량 ${requested}개, 주문 가능 수량 ${available}개입니다.",
        ) {
            // detail은 사람이 읽는 문장이다. 클라이언트가 문자열을 파싱하게 두면 안 된다.
            // RFC 9457은 확장 필드를 허용한다. 기계가 읽을 값은 여기에 넣는다.
            setProperty("productId", productId)
            setProperty("requested", requested)
            setProperty("available", available)
        }

        // 재시도 한도를 넘긴 낙관적 락 충돌(2단계). 역시 다시 시도하면 될 수 있다.
        is OrderError.ConflictExhausted -> problem(
            status = HttpStatus.CONFLICT,
            type = "conflict-exhausted",
            title = "동시 요청 충돌",
            detail = "${attempts}회 재시도했지만 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        ) { setProperty("attempts", attempts) }

        // 이미 확정되거나 만료된 선점(3단계). 자원의 현재 상태와 맞지 않는 요청이다.
        OrderError.ReservationAlreadySettled -> problem(
            status = HttpStatus.CONFLICT,
            type = "reservation-already-settled",
            title = "이미 처리된 선점",
            detail = "해당 선점은 이미 확정되었거나 만료되었습니다.",
        )

        is OrderError.InvalidStatusTransition -> problem(
            status = HttpStatus.CONFLICT,
            type = "invalid-status-transition",
            title = "허용되지 않는 상태 전이",
            detail = "${from}에서 ${to}(으)로 바꿀 수 없습니다.",
        ) {
            setProperty("from", from.name)
            setProperty("to", to.name)
        }

        // 404 — 자원이 없다.
        is OrderError.ProductNotFound -> problem(
            status = HttpStatus.NOT_FOUND,
            type = "product-not-found",
            title = "상품을 찾을 수 없음",
            detail = "상품 ${productId}이(가) 존재하지 않습니다.",
        ) { setProperty("productId", productId) }

        is OrderError.OrderNotFound -> problem(
            status = HttpStatus.NOT_FOUND,
            type = "order-not-found",
            title = "주문을 찾을 수 없음",
            detail = "주문 ${orderId}이(가) 존재하지 않습니다.",
        ) { setProperty("orderId", orderId) }

        // 400 — 요청 자체가 틀렸다. 서버 상태와 무관하므로 그대로 다시 보내도 똑같이 실패한다.
        //       409와 갈리는 기준이 여기다: "다시 시도해서 달라질 여지가 있는가".
        is OrderError.InvalidQuantity -> problem(
            status = HttpStatus.BAD_REQUEST,
            type = "invalid-quantity",
            title = "잘못된 수량",
            detail = "수량은 1개 이상이어야 합니다. (상품 ${productId}, 요청 ${quantity})",
        ) {
            setProperty("productId", productId)
            setProperty("quantity", quantity)
        }

        OrderError.EmptyOrder -> problem(
            status = HttpStatus.BAD_REQUEST,
            type = "empty-order",
            title = "빈 주문",
            detail = "주문 항목이 하나 이상 필요합니다.",
        )
    }

    return ResponseEntity.status(problem.status).body(problem)
}

/** ProblemDetail 조립 보일러플레이트를 한 곳에 모은다. */
private fun problem(
    status: HttpStatus,
    type: String,
    title: String,
    detail: String,
    extensions: ProblemDetail.() -> Unit = {},
): ProblemDetail = ProblemDetail.forStatus(status).apply {
    this.type = URI.create("$PROBLEM_TYPE_BASE/$type")
    this.title = title
    this.detail = detail
    extensions()
}

/**
 * type은 에러 "종류"의 식별자다. 실제로 접속되는 주소일 필요는 없지만,
 * 문서 페이지를 두면 클라이언트 개발자가 바로 찾아볼 수 있어 URI를 쓴다.
 */
private const val PROBLEM_TYPE_BASE = "https://commerce-lab.dev/problems"
