package com.commercelab.bootstrap.web.order

import com.commercelab.order.api.ProductCatalog
import com.commercelab.order.api.ProductView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 상품 목록.
 *
 * 이 컨트롤러는 2026-08-25까지 **빈 스텁**이었다. `Unit`을 반환해서
 * `200 OK` + 본문 없음이 나갔고, 명세에도 200으로 박혔다.
 *
 * 404보다 나빴다. k6 부하 스크립트의 teardown이 `status !== 200`으로 막고 있었는데
 * 그 가드를 통과한 뒤 `res.json()`에서 빈 본문을 파싱하다 크래시했다.
 * **"성공했다"고 거짓말하는 응답은 호출자의 오류 처리를 통째로 무력화한다.**
 * 아직 못 만든 것은 없다고 말하는 편이 낫다.
 */
@RestController
class ProductController(
    private val productCatalog: ProductCatalog,
) {

    @Operation(summary = "상품 목록", description = "활성 상품과 현재 재고. available = total - reserved")
    @ApiResponse(responseCode = "200", description = "상품 목록")
    @GetMapping("/api/products")
    fun getProducts(): List<ProductView> = productCatalog.findAll()
}
