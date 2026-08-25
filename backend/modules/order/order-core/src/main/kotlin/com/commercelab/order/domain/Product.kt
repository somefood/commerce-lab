package com.commercelab.order.domain

import com.commercelab.common.Money
import com.commercelab.order.api.OrderException

/**
 * `id`에 기본값이 없는 이유 (2026-08-25에 `UUID.randomUUID()` 기본값을 지웠다):
 *
 * 이 객체는 언제나 DB에서 읽어온다. 애플리케이션이 상품을 만들지 않는다.
 * 기본값이 있으면 매핑에서 `id`를 빠뜨려도 컴파일이 통과하고, 실행하면
 * **DB의 상품과 아무 관계 없는 임의의 UUID를 가진 Product**가 조용히 만들어진다.
 * 그 값으로 주문 라인을 만들면 존재하지 않는 상품을 가리키는 주문이 생긴다.
 *
 * 기본값을 지우면 그 실수가 컴파일 에러가 된다. 기본값은 "안 줘도 되는 값"에만 붙인다.
 */
data class Product(
    val id: String,
    val unitAmount: Money,
    val active: Boolean,
) {
    /**
     * 이 상품으로 주문 라인을 만든다.
     *
     * 비활성 상품 검사가 여기 있는 이유:
     * DDL 주석이 "비활성 상품 주문 금지는 도메인이 강제한다"고 선언해놨는데
     * 정작 도메인이 `active`를 몰랐다(§13-1의 빚). 규칙을 선언한 자리와
     * 강제하는 자리가 다르면 그 선언은 주석일 뿐이다.
     *
     * 서비스가 `if (!product.active) throw ...`를 하지 않고 이 함수를 부르는 이유:
     * 상품을 주문 라인으로 바꾸는 경로가 하나뿐이면 검사를 빠뜨릴 자리가 없다.
     * 호출자가 검사를 기억해야 하는 구조는 언젠가 잊힌다.
     *
     * @throws OrderException.ProductInactive 판매 중지된 상품일 때
     */
    fun lineFor(quantity: Int): OrderLine {
        if (!active) {
            throw OrderException.ProductInactive(id)
        }
        return OrderLine(productId = id, quantity = quantity, unitAmount = unitAmount)
    }
}
