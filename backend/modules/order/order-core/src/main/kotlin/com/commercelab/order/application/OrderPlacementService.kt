package com.commercelab.order.application

import com.commercelab.common.DomainResult
import com.commercelab.order.api.OrderError
import com.commercelab.order.api.OrderPlacement
import com.commercelab.order.api.PlaceOrder
import com.commercelab.order.api.PlaceOrderCommand
import com.commercelab.order.domain.Inventory
import com.commercelab.order.domain.InventoryRepository
import com.commercelab.order.domain.Order
import com.commercelab.order.domain.OrderLine
import com.commercelab.order.domain.OrderRepository
import com.commercelab.order.domain.ProductRepository
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [1단계] 락이 없다. 의도적으로 그렇다.
 *
 * 재고를 읽고 → 검사하고 → 쓴다. 세 단계 사이에 다른 트랜잭션이 끼어들 수 있고,
 * 그 결과가 오버셀과 갱신 손실이다. M1 §8 표의 1행이 그 관측 결과다.
 * 2단계에서 이 틈을 낙관적 락으로 막는다.
 */
@Service
class OrderPlacementService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
) : OrderPlacement {

    /**
     * 메서드가 두 구간으로 나뉜다. **읽고 계산하는 구간**과 **쓰는 구간**이다.
     *
     * 왜 이렇게 나눴나 — 스프링은 예외를 보고 롤백을 결정한다. 반환값은 보지 않는다.
     * DomainResult.failure를 return 하는 것은 정상 종료이므로 트랜잭션이 그대로 커밋된다.
     * 주문을 먼저 저장한 뒤 재고 부족으로 실패를 반환하면, 실패했다고 응답해놓고
     * orders 행은 남는다. (실제로 그렇게 동작하는 것을 확인했다: 성공 1건인데 orders=2)
     *
     * 해결책은 두 가지였다.
     *   (가) 쓰기 전에 모든 검증을 끝낸다        ← 택함
     *   (나) 실패 시 setRollbackOnly()로 롤백을 지시한다
     *
     * (가)를 택한 이유: 롤백은 "쓴 것을 되돌리는" 비용을 치른다. 애초에 쓰지 않으면
     * 되돌릴 것도 없다. 그리고 (나)는 TransactionAspectSupport라는 스프링 내부 API를
     * 애플리케이션 서비스에 들여온다 — 트랜잭션 관리 방식이 도메인 흐름에 새어 나온다.
     *
     * 대가: 검증 구간에서 만든 계산 결과(reservedInventories)를 들고 있어야 한다.
     *       라인이 많아지면 메모리에 쌓인다. 주문 한 건의 라인 수는 유한하므로 감수한다.
     */
    @Transactional
    override fun place(command: PlaceOrderCommand): DomainResult<OrderError, PlaceOrder> {
        // ───────── 읽기·계산 구간: 여기서는 아무것도 쓰지 않는다 ─────────

        // findByIds는 존재하는 것만 돌려준다. 요청한 개수와 다를 수 있다.
        // id로 색인해두면 라인마다 리스트를 훑지 않아도 된다(N번의 O(n) → N번의 O(1)).
        val products = productRepository.findByIds(command.lines.map { it.productId })
            .associateBy { it.id }

        // map은 inline 함수라 람다 안의 return이 place() 자체를 빠져나간다(비지역 반환).
        // 상품이 없으면 그 라인을 건너뛰지 않는다 — 건너뛰면 사용자가 주문하지 않은 주문이 생기고,
        // 전부 걸러졌을 때는 원인이 "상품 없음"인데 에러는 EmptyOrder라고 말하게 된다.
        val orderLines = command.lines.map { line ->
            val product = products[line.productId]
                ?: return DomainResult.failure(OrderError.ProductNotFound(line.productId))
            OrderLine(
                productId = line.productId,
                quantity = line.quantity,
                unitAmount = product.unitAmount,
            )
        }

        val order = when (val placed = Order.place(
            orderId = UUID.randomUUID().toString(),
            accountId = command.accountId,
            lines = orderLines,
            // TODO(3단계): Clock을 주입받아 Instant.now(clock)으로 바꾼다.
            //  만료 테스트는 "만료 3초 전" 같은 시점을 만들어야 하는데 지금은 고정할 수 없다.
            now = Instant.now(),
        )) {
            is DomainResult.Failure -> return placed
            is DomainResult.Success -> placed.value
        }

        // 같은 상품이 여러 라인에 나뉘어 들어올 수 있다(예: 같은 상품 2줄).
        // 라인마다 따로 재고를 읽으면 두 번 모두 같은 값을 보고 각자 검사를 통과한다 —
        // 한 주문 안에서 오버셀이 나는 셈이다. 상품 단위로 합쳐서 한 번만 검사한다.
        val requestedByProduct = order.lines
            .groupBy { it.productId }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity } }

        val reservedInventories = mutableListOf<Inventory>()
        requestedByProduct.forEach { (productId, quantity) ->
            val inventory = inventoryRepository.findByProductId(productId)
            when (val reserved = inventory.addReserved(quantity)) {
                is DomainResult.Failure -> return reserved
                is DomainResult.Success -> reservedInventories += reserved.value
            }
        }

        // ───────── 쓰기 구간: 여기부터는 실패로 빠져나가지 않는다 ─────────

        orderRepository.save(order)
        reservedInventories.forEach(inventoryRepository::updateReserveQuantity)

        return DomainResult.success(
            PlaceOrder(
                orderId = order.orderId,
                status = order.status,
                totalAmount = order.totalAmount.amount,
                // 3단계에서 선점 행을 만들면 채운다.
                reservations = emptyList(),
            )
        )
    }
}
