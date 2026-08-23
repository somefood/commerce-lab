package com.commercelab.bootstrap.web.dto

/**
 * 테스트 무대를 세우는 요청. 상품 하나와 그 재고를 알려진 상태로 되돌린다.
 *
 * name / unitAmount에 기본값이 있는 이유:
 * 동시성 테스트가 궁금한 건 "재고 50에 100건이 들어오면 몇 건이 성공하는가"뿐이다.
 * 상품 이름이나 단가는 그 질문과 무관하다. 매번 적게 만들면 테스트 본문이
 * 관심 없는 값으로 길어진다. 다만 컬럼이 NOT NULL이라 값 자체는 있어야 한다.
 *
 * Kotlin의 기본값을 Jackson이 쓰려면 jackson-module-kotlin이 필요하다.
 * 없으면 기본값은커녕 역직렬화 자체가 실패한다 (bootstrap/build.gradle.kts 참고).
 */
data class DevResetRequest(
    val productId: String,
    val total: Int,
    val name: String = productId,
    val unitAmount: Long = 10_000,
)

data class DevResetResponse(
    val productId: String,
    val total: Int,
    /** 이 상품이 걸려 있어 지워진 주문 수. 초기화가 실제로 무언가를 했는지 보여준다. */
    val deletedOrders: Int,
)
