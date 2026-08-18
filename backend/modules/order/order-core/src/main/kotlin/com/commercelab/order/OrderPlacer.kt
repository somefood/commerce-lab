package com.commercelab.order

import com.commercelab.common.Money
import com.commercelab.contract.OrderPlaced
import java.time.Instant

/**
 * M0 시점의 자리표. M1에서 실제 주문 생성 로직으로 교체된다.
 * 지금은 ArchUnit 규칙이 검사할 대상이 존재하게 하는 것이 목적이다.
 */
class OrderPlacer {

    fun place(orderId: String, accountId: String, amount: Money): OrderPlaced =
        OrderPlaced(
            eventId = orderId,
            orderId = orderId,
            accountId = accountId,
            amount = amount.amount,
            occurredAt = Instant.EPOCH,
        )
}
