package com.commercelab.order.infrastructure

import com.commercelab.order.api.ProductCatalog
import com.commercelab.order.api.ProductView
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * 조회 결과 한 행. `infrastructure`가 소유한다.
 *
 * order-api의 [ProductView]를 JPQL이 직접 만들게 하지 않는 이유:
 * 그러면 모듈의 공개 계약이 JPQL 생성자 표현식의 인자 순서에 묶인다.
 * `ProductView`에 필드를 하나 끼워 넣는 순간 런타임에 깨지고, 컴파일러는 침묵한다.
 * 여기서 한 번 받아 [toView]로 옮기면 그 결합이 어댑터 안에서 끝난다.
 */
data class ProductInventoryRow(
    val productId: String,
    val name: String,
    val unitAmount: Long,
    val total: Int,
    val reserved: Int,
)

interface ProductCatalogJpaRepository : JpaRepository<ProductEntity, String> {

    /**
     * `products`와 `inventories`를 조인한다.
     *
     * 두 엔티티에 연관관계 매핑이 없어 `on`으로 직접 잇는다(HQL의 엔티티 조인).
     * 매핑을 넣지 않는 이유: 쓰기 경로는 상품과 재고를 따로 다룬다. 조회 한 곳 편하자고
     * 연관을 걸면 쓰기 쪽에 지연 로딩과 영속성 전이라는 부작용이 딸려 온다.
     *
     * `join`이라 재고 행이 없는 상품은 빠진다. `left join`이면 재고를 0으로 보여주는데,
     * 그건 "재고 없음"과 "재고 행 자체가 없음"을 같은 것으로 만든다.
     * 후자는 데이터 사고이므로 조용히 0으로 덮지 않는다.
     *
     * 스키마 간 조인이 아니다 — 둘 다 `order` 스키마다(CLAUDE.md §5).
     */
    @Query(
        """
        select new com.commercelab.order.infrastructure.ProductInventoryRow(
            p.id, p.name, p.unitAmount, i.total, i.reserved
        )
        from ProductEntity p
        join InventoryEntity i on i.productId = p.id
        where p.active = true
        order by p.id
        """
    )
    fun findCatalog(): List<ProductInventoryRow>
}

@Repository
class ProductCatalogAdapter(
    private val repository: ProductCatalogJpaRepository,
) : ProductCatalog {

    override fun findAll(): List<ProductView> = repository.findCatalog().map { it.toView() }
}

private fun ProductInventoryRow.toView() = ProductView(
    productId = productId,
    name = name,
    unitAmount = unitAmount,
    total = total,
    reserved = reserved,
)
