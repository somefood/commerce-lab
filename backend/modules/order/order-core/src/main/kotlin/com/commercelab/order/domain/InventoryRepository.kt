package com.commercelab.order.domain

/**
 * 포트는 사실만 반환한다. 그 사실이 실패인지는 application이 정한다.
 *
 * `findByProductId`가 `Inventory?`인 이유 (2026-08-25에 바꿨다):
 * 어댑터가 `IllegalArgumentException("Product not found")`을 던지고 있었다.
 * 두 가지가 잘못됐다.
 *   1. 재고 행이 없는 것과 상품이 없는 것은 다른 사건인데 같은 메시지로 뭉갰다
 *   2. **저장소가 "이건 에러다"를 혼자 정했다.** 같은 "없음"이 호출 맥락에 따라
 *      정상일 수도 있다 — 예를 들어 재고 등록 여부를 확인하는 코드에게는 null이 답이다
 *
 * 지금은 "없다"는 사실만 돌려준다. 주문 경로에서 재고 행이 없는 것은
 * `products`와 `inventories`가 어긋났다는 뜻이므로 application이 사고로 처리한다.
 */
interface InventoryRepository {

    /** 재고 행이 없으면 null. */
    fun findByProductId(productId: String): Inventory?

    /** @throws IllegalStateException 갱신 대상 행이 없을 때 — 읽고 나서 사라진 것이므로 사고다 */
    fun updateReserveQuantity(inventory: Inventory)
}
