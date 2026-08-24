package com.commercelab.order.application

import com.commercelab.order.api.OrderException
import com.commercelab.order.api.OrderPlacement
import com.commercelab.order.api.PlaceOrder
import com.commercelab.order.api.PlaceOrderCommand
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
     * ## 이전 버전과 무엇이 다른가 (2026-08-24)
     *
     * 이전에는 이 메서드가 **읽기·계산 구간**과 **쓰기 구간**으로 갈라져 있었다.
     * 실패를 `DomainResult.failure`로 *반환*했기 때문이다 — 스프링은 예외를 보고
     * 롤백을 결정하고 반환값은 보지 않으므로, 실패를 반환하면 그때까지 쓴 것이 그대로
     * 커밋된다. 그래서 "쓰기 전에 모든 검증을 끝낸다"는 규율로 막아야 했다.
     *
     * 실패가 예외가 된 지금은 그 규율이 필요 없다. **아래 순서를 보라 —
     * 주문을 먼저 저장하고, 그다음에 재고를 검사한다.** 재고가 모자라면
     * `Inventory.addReserved`가 `OutOfStock`을 던지고, `@Transactional`이
     * 이미 저장한 orders/order_lines 행까지 함께 되돌린다.
     *
     * 이 순서는 실수가 아니라 **이번 전환이 실제로 동작한다는 증거**다.
     * `OrderRollbackIntegrationTest`가 정확히 이 경로를 검사한다 —
     * 409를 받았을 때 orders 테이블이 비어 있는지.
     * 이전 방식이었다면 여기서 주문 행이 남았을 것이다(실측: 성공 1건인데 orders=2).
     *
     * 버린 대안: `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`.
     * 스프링 내부 API가 애플리케이션 서비스에 들어오고, 예외를 쓰면 애초에 필요가 없다.
     *
     * @throws OrderException 예상된 비즈니스 실패. 종류는 [OrderPlacement.place] 참고
     */
    @Transactional
    override fun place(command: PlaceOrderCommand): PlaceOrder {
        // findByIds는 존재하는 것만 돌려준다. 요청한 개수와 다를 수 있다.
        // id로 색인해두면 라인마다 리스트를 훑지 않아도 된다(N번의 O(n) → N번의 O(1)).
        val products = productRepository.findByIds(command.lines.map { it.productId })
            .associateBy { it.id }

        // 상품이 없으면 그 라인을 건너뛰지 않고 주문 전체를 실패시킨다.
        // 건너뛰면 사용자가 주문하지 않은 주문이 생기고, 전부 걸러졌을 때는
        // 원인이 "상품 없음"인데 에러는 EmptyOrder라고 말하게 된다.
        val orderLines = command.lines.map { line ->
            val product = products[line.productId]
                ?: throw OrderException.ProductNotFound(line.productId)
            OrderLine(
                productId = line.productId,
                quantity = line.quantity,
                unitAmount = product.unitAmount,
            )
        }

        val order = Order.place(
            orderId = UUID.randomUUID().toString(),
            accountId = command.accountId,
            lines = orderLines,
            // TODO(2단계 전): Clock을 주입받아 Instant.now(clock)으로 바꾼다.
            //  3단계 만료 테스트는 "만료 3초 전" 같은 시점을 만들어야 하는데 지금은 고정할 수 없다.
            now = Instant.now(),
        )

        // 재고 검사보다 먼저 쓴다. 위 KDoc 참고 — 실패하면 롤백된다.
        orderRepository.save(order)

        // 같은 상품이 여러 라인에 나뉘어 들어올 수 있다(예: 같은 상품 2줄).
        // 라인마다 따로 재고를 읽으면 두 번 모두 같은 값을 보고 각자 검사를 통과한다 —
        // 한 주문 안에서 오버셀이 나는 셈이다. 상품 단위로 합쳐서 한 번만 검사한다.
        val requestedByProduct = order.lines
            .groupBy { it.productId }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity } }

        requestedByProduct.forEach { (productId, quantity) ->
            val inventory = inventoryRepository.findByProductId(productId)
            inventoryRepository.updateReserveQuantity(inventory.addReserved(quantity))
        }

        return PlaceOrder(
            orderId = order.orderId,
            status = order.status,
            totalAmount = order.totalAmount.amount,
            // 3단계에서 선점 행을 만들면 채운다.
            reservations = emptyList(),
        )
    }
}
