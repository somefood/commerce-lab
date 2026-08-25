package com.commercelab.order.infrastructure

import com.commercelab.order.domain.Inventory
import com.commercelab.order.domain.InventoryRepository
import org.springframework.stereotype.Repository

@Repository
class InventoryRepositoryAdapter(
    private val inventoryJpaRepository: InventoryJpaRepository,
) : InventoryRepository {

    /**
     * 파생 쿼리(`findByProductId`)가 아니라 `findById`를 쓴다.
     *
     * `InventoryEntity`의 `@Id`가 곧 `productId`라 둘은 같은 행을 찾는다. 그런데
     * **파생 쿼리는 영속성 컨텍스트를 건너뛰고 항상 DB로 나간다.**
     * `findById`만 1차 캐시를 먼저 본다.
     *
     * 실측(주문 1건): 이 메서드와 [updateReserveQuantity]가 각각 파생 쿼리를 쓸 때
     * `select inventories`가 **두 번** 나갔다. 바꾸니 한 번이 된다.
     * 주문에 든 상품 수만큼 곱해지던 왕복이다.
     */
    override fun findByProductId(productId: String): Inventory? =
        inventoryJpaRepository.findById(productId).orElse(null)?.toDomain()

    /**
     * 이전에는 대상 행이 없으면 `?: return`으로 조용히 아무것도 하지 않았다.
     * 호출자는 갱신에 성공했다고 믿는다 — **"성공했다고 거짓말하는" 실패다.**
     * 이 마일스톤에서 같은 패턴을 세 번 만났다(200을 반환하는 빈 스텁,
     * 실패할 수 없는 단언, 오버셀 0건 오보). 조용한 no-op은 그중 제일 찾기 어렵다.
     *
     * 여기까지 왔다는 것은 방금 `findByProductId`로 읽어온 행이 사라졌다는 뜻이다.
     * 정상 운영에서는 일어날 수 없으므로 `OrderException`이 아니라 사고로 던진다 → 500.
     */
    override fun updateReserveQuantity(inventory: Inventory) {
        val entity = inventoryJpaRepository.findById(inventory.productId).orElse(null)
            ?: error("재고 행이 사라졌다: ${inventory.productId}")
        entity.reserved = inventory.reserved
    }
}

private fun InventoryEntity.toDomain(): Inventory = Inventory(
    productId = productId,
    total = total,
    reserved = reserved,
    version = version,
)
