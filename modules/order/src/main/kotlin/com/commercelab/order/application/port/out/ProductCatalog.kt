package com.commercelab.order.application.port.out

import com.commercelab.common.Money

/**
 * order가 상품 세계에 요구하는 능력의 선언.
 *
 * 소유자는 order다 — "필요한 쪽이 인터페이스를 소유한다" (PasswordHasher, TokenIssuer와 같은 원리).
 * product 모듈은 이 인터페이스의 존재를 모르고, order는 product의 내부(도메인/포트)를 모른다.
 * 둘을 잇는 건 어댑터 한 파일뿐이다.
 */
interface ProductCatalog {

    /** 없는 상품이면 null. "없음"을 어떤 예외로 번역할지는 호출자(서비스)가 정한다. */
    fun findProduct(productId: Long): CatalogProduct?
}

/**
 * order 관점의 상품 최소 뷰.
 *
 * product의 도메인 객체(Product)를 그대로 반환하지 않는 이유:
 * - Product가 바뀔 때마다 order가 흔들린다 (결합)
 * - order에게 stockQuantity 같은 내부 사정을 보여줄 이유가 없다 —
 *   재고 차감은 product에게 "요청"할 일이지 order가 직접 계산할 일이 아니다
 */
data class CatalogProduct(
    val id: Long,
    val name: String,
    val price: Money,
    /** 판매 가능 여부. soft delete(INACTIVE) 여부를 order의 언어로 번역한 것 */
    val available: Boolean,
)
