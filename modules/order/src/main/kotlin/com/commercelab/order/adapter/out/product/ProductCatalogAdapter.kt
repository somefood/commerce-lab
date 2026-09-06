package com.commercelab.order.adapter.out.product

import com.commercelab.order.application.port.out.CatalogProduct
import com.commercelab.order.application.port.out.ProductCatalog
import com.commercelab.product.application.port.`in`.GetProductQuery
import com.commercelab.product.domain.ProductStatus
import org.springframework.stereotype.Component

/**
 * ProductCatalog 포트의 구현 — order 모듈에서 유일하게 product를 아는 파일.
 *
 * 지금은 같은 JVM 안의 유스케이스 호출이지만, Phase 8에서 서비스가 분리되면
 * 이 파일만 HTTP 클라이언트로 교체된다. order의 서비스/도메인은 한 줄도 안 바뀐다.
 */
@Component
class ProductCatalogAdapter(
    private val getProductQuery: GetProductQuery,
) : ProductCatalog {

    override fun findProduct(productId: Long): CatalogProduct? {
        // 계약 번역: product는 "없으면 예외", 우리 포트는 "없으면 null".
        // 서로 다른 계약을 맞추는 것이 어댑터의 존재 이유다.
        val product = try {
            getProductQuery.getProduct(productId)
        } catch (e: NoSuchElementException) {
            return null
        }

        return CatalogProduct(
            id = requireNotNull(product.id) { "저장된 상품은 id가 있어야 합니다" },
            name = product.name,
            price = product.price,
            available = product.status == ProductStatus.ACTIVE,
        )
    }
}
