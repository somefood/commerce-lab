package com.commercelab.contract

import java.time.Instant

/**
 * 주문이 생성되었음을 알리는 이벤트.
 *
 * 원시 타입만 쓰는 이유: M4에서 payment가 별도 프로세스로 분리되면 이 계약은
 * JSON으로 직렬화된다. Money 같은 도메인 타입이 계약에 새면 양쪽 서비스가
 * 같은 클래스를 공유해야 하고, 그 순간 독립 배포가 불가능해진다.
 */
data class OrderPlaced(
    val eventId: String,
    val orderId: String,
    val accountId: String,
    val amount: Long,
    val occurredAt: Instant,
)
