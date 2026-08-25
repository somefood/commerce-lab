package com.commercelab.order.api

/**
 * 상품 목록 조회의 읽기 모델.
 *
 * 도메인의 `Product`와 다른 타입인 이유:
 * 도메인 `Product`는 주문을 만들 때 필요한 것(단가)만 안다. 여기에는 `name`처럼
 * 도메인이 몰라도 되는 표시용 값과, `products`·`inventories` 두 테이블을 합쳐야
 * 나오는 값이 섞여 있다. 쓰기 모델에 표시용 필드를 얹기 시작하면
 * 도메인이 화면 요구에 끌려다닌다.
 *
 * [available]이 계산 프로퍼티인 이유:
 * `total - reserved`는 언제나 참인 관계다. 필드로 들고 있으면 셋이 어긋날 수 있고,
 * 어긋났을 때 무엇이 진실인지 정할 방법이 없다. **파생값은 저장하지 않고 계산한다.**
 * (M2 원장에서 같은 질문이 다시 나온다 — 그때는 계산 비용 때문에 답이 달라질 수 있다)
 */
data class ProductView(
    val productId: String,
    val name: String,
    val unitAmount: Long,
    val total: Int,
    val reserved: Int,
) {
    val available: Int get() = total - reserved
}

/**
 * 상품 목록 조회 포트. 쓰기 경로([OrderPlacement])와 분리돼 있다.
 *
 * 조회는 애그리거트를 거치지 않는다. 화면에 필요한 모양으로 바로 읽는 편이
 * 도메인 객체를 만들었다가 DTO로 다시 옮기는 것보다 단순하고 빠르다.
 * (설계문서의 "JPA는 쓰기, jOOQ는 조회" 방침이 이 갈래다. jOOQ는 아직 안 붙였다)
 */
interface ProductCatalog {
    /** 활성 상품만 돌려준다. 재고 행이 없는 상품은 팔 수 없으므로 목록에도 없다. */
    fun findAll(): List<ProductView>
}
