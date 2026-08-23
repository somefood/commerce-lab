package com.commercelab.bootstrap.web

import com.commercelab.bootstrap.web.dto.DevResetRequest
import com.commercelab.bootstrap.web.dto.DevResetResponse
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 테스트·부하 시나리오 전용 초기화 엔드포인트. dev 프로파일에서만 등록된다.
 *
 * 도메인이 아니라 JdbcTemplate로 직접 SQL을 쓰는 이유:
 * 여기는 도메인 규칙을 실행하는 곳이 아니라, 도메인 규칙을 관측하기 위한 무대를 세우는 곳이다.
 * 주문 서비스를 거쳐 재고를 만들면 "검증 대상"과 "검증 준비"가 같은 코드가 되고,
 * 그 코드에 버그가 있을 때 어느 쪽이 틀렸는지 구분할 수 없게 된다.
 *
 * 트랜잭션이 없는 이유:
 * bootstrap에는 @Transactional을 붙일 수 없다(ArchUnit `bootstrap 클래스에는 트랜잭션 경계가 없다`).
 * 필요하지도 않다 — 초기화는 부하가 시작되기 전에 단독으로 한 번 실행된다.
 * 세 문장 사이로 다른 요청이 끼어드는 상황 자체가 없다.
 */
@Profile("dev")
@RestController
class DevController(
    private val jdbc: JdbcTemplate,
) {

    @PostMapping("/api/dev/reset")
    fun devReset(@RequestBody request: DevResetRequest): DevResetResponse {
        upsertProduct(request)
        val deletedOrders = deleteOrdersOf(request.productId)
        upsertInventory(request.productId, request.total)

        return DevResetResponse(
            productId = request.productId,
            total = request.total,
            deletedOrders = deletedOrders,
        )
    }

    /**
     * 1) 상품부터. inventories와 order_lines가 products를 FK로 참조하므로 이게 먼저 있어야 한다.
     *
     * ON CONFLICT DO UPDATE는 Postgres의 upsert다. "없으면 넣고 있으면 고친다"를 한 문장으로
     * 처리한다. SELECT로 존재를 확인하고 분기하면 그 사이에 다른 세션이 끼어들 수 있다 —
     * 이 엔드포인트에서는 문제가 안 되지만, 습관으로 만들면 곤란한 패턴이다.
     * excluded는 "INSERT하려다 충돌한 그 행"을 가리키는 Postgres의 특수 별칭이다.
     *
     * active = true로 되돌리는 이유: 이전 시나리오가 비활성화해 두었을 수 있다.
     * 초기화는 "직전에 무슨 일이 있었든 같은 상태"를 만들어야 한다.
     */
    private fun upsertProduct(request: DevResetRequest) {
        jdbc.update(
            """
            INSERT INTO "order".products (id, name, unit_amount, active)
            VALUES (?, ?, ?, true)
            ON CONFLICT (id) DO UPDATE
               SET name        = excluded.name,
                   unit_amount = excluded.unit_amount,
                   active      = true
            """.trimIndent(),
            request.productId,
            request.name,
            request.unitAmount,
        )
    }

    /**
     * 2) 이 상품이 걸린 주문을 지운다.
     *
     * order_lines와 reservations의 FK는 ON DELETE CASCADE다. 부모인 orders를 지우면
     * 자식은 DB가 알아서 지운다. 자식부터 하나씩 지우는 건 CASCADE를 걸어둔 의미가 없다.
     *
     * 상품 전체가 아니라 "이 상품이 걸린 주문"만 지운다. 초기화의 범위가 요청의 범위와
     * 같아야 한다 — 다른 상품 시나리오를 조용히 밟아버리면 병렬 실행이 불가능해진다.
     */
    private fun deleteOrdersOf(productId: String): Int =
        jdbc.update(
            """
            DELETE FROM "order".orders
             WHERE id IN (
                   SELECT order_id
                     FROM "order".order_lines
                    WHERE product_id = ?
             )
            """.trimIndent(),
            productId,
        )

    /**
     * 3) 마지막에 재고. 순서가 중요하다.
     *
     * reserved = 0으로 먼저 되돌리고 주문을 지우면, 그 사이에는 "선점은 살아 있는데
     * 재고는 아무도 안 잡은 것으로 보이는" 상태가 존재한다. 지금은 부하 전이라 아무도 못 보지만,
     * 상태를 되돌리는 코드는 항상 "참조하는 쪽을 먼저 치우고 참조되는 쪽을 정리"해야 한다.
     */
    private fun upsertInventory(productId: String, total: Int) {
        jdbc.update(
            """
            INSERT INTO "order".inventories (product_id, total, reserved, version)
            VALUES (?, ?, 0, 0)
            ON CONFLICT (product_id) DO UPDATE
               SET total    = excluded.total,
                   reserved = 0,
                   version  = 0
            """.trimIndent(),
            productId,
            total,
        )
    }
}
