package com.commercelab.order.domain

import com.commercelab.order.api.OrderException

data class Inventory(
    val productId: String,
    val total: Int,
    val reserved: Int,
    val version: Int,
) {
    /**
     * 두 실패가 서로 다른 종류라는 점이 이 함수의 핵심이다.
     *
     * - 음수 수량: **일어나면 버그다.** 컨트롤러와 Order.place가 이미 걸렀어야 한다.
     *   여기까지 내려왔다면 위쪽 검증이 뚫린 것이므로 500이 맞다.
     *   그래서 OrderException을 상속하지 않는 IllegalArgumentException을 던진다.
     * - 재고 부족: **매일 일어나는 정상적인 결과다.** 사용자에게 알려줄 일이지 사고가 아니다.
     *   OrderException이므로 409로 나간다.
     *
     * 실패를 전부 예외로 바꾸면 이 구분이 문법에서 사라진다. 타입 계층이 그 자리를 대신한다 —
     * OrderException을 상속하는가 아닌가가 4xx와 5xx를 가르는 유일한 선이다.
     *
     * @throws OrderException.OutOfStock 남은 수량보다 많이 잡으려 할 때
     */
    fun addReserved(newReserve: Int): Inventory {
        require(newReserve >= 0) { "Reserved quantity cannot be negative: $newReserve" }

        val available = total - reserved
        if (newReserve > available) {
            throw OrderException.OutOfStock(productId, newReserve, available)
        }
        return copy(reserved = reserved + newReserve)
    }
}
