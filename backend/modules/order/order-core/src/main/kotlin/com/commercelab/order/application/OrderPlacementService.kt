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
import java.time.Clock
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
    // 시간을 주입받는다. Instant.now()를 직접 부르면 3단계 만료 테스트가
    // "만료 3초 전" 같은 시점을 만들 수 없다. 빈 설명은 bootstrap의 ClockConfig에.
    private val clock: Clock,
) : OrderPlacement {

    /**
     * ## 실패는 예외로 나간다 — 롤백을 스프링에 맡긴다
     *
     * 예전에는 실패를 `DomainResult.failure`로 *반환*했다. 스프링은 예외를 보고 롤백을
     * 결정하고 반환값은 보지 않으므로, 그때는 "쓰기 전에 모든 검증을 끝낸다"는 규율을
     * 사람이 져야 했다. 지금은 예외라 그 규율이 필요 없다 (M1 §4-2).
     *
     * ## 그런데도 검증을 먼저 하는 이유 (2026-08-25)
     *
     * 롤백에 기댈 수 있다고 해서 기대야 하는 것은 아니다.
     * 한때 이 메서드는 **주문을 먼저 저장하고 재고를 나중에 검사**했다.
     * 롤백이 실제로 도는 것을 보여주려는 의도였는데, 리뷰에서 지적이 나왔고 실측해보니
     * 409로 끝나는 요청 하나가 이런 SQL을 냈다.
     *
     * ```
     * select products      → select orders(merge) → insert orders → insert order_lines
     * → select inventories ← 이 조회가 위 INSERT들을 flush시킨다 (FlushMode.AUTO)
     * ```
     *
     * **INSERT가 영속성 컨텍스트에만 머무는 게 아니라 실제로 DB에 도달한 뒤 롤백된다.**
     * 롤백된 INSERT도 WAL을 쓰고 죽은 튜플을 남겨 autovacuum이 치워야 한다.
     * 재고가 소진된 뒤에는 **모든 요청**이 이 경로를 탄다.
     *
     * 그래서 순서를 되돌렸다. **읽고 → 검증하고 → 쓴다.**
     * 롤백은 여전히 안전망이지만, 일상적으로 밟는 길이 아니다.
     *
     * ## 그럼 롤백은 무엇이 증명하나
     *
     * 이 순서로도 부분 쓰기는 남는다. 재고 예약이 **상품 단위로 순차 처리**되기 때문이다 —
     * 라인이 둘이면 앞 상품의 `reserved`를 올린 뒤 뒤 상품에서 `OutOfStock`이 날 수 있다.
     * `OrderRollbackIntegrationTest`의 `여러 상품 중 하나만 재고가 모자라면...`이 그 경로다.
     * 검증을 아무리 앞으로 당겨도 이 부분 쓰기는 없앨 수 없으므로,
     * **롤백 테스트는 이 메서드의 순서가 어떻게 바뀌든 계속 의미를 갖는다.**
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
            // 라인 조립을 도메인에 맡긴다. 비활성 상품 검사가 그 안에 있다 —
            // 서비스가 검사를 기억해야 하는 구조는 언젠가 잊힌다.
            product.lineFor(line.quantity)
        }

        val order = Order.place(
            orderId = UUID.randomUUID().toString(),
            accountId = command.accountId,
            lines = orderLines,
            now = Instant.now(clock),
        )

        // 같은 상품이 여러 라인에 나뉘어 들어올 수 있다(예: 같은 상품 2줄).
        // 라인마다 따로 재고를 읽으면 두 번 모두 같은 값을 보고 각자 검사를 통과한다 —
        // 한 주문 안에서 오버셀이 나는 셈이다. 상품 단위로 합쳐서 한 번만 검사한다.
        val requestedByProduct = order.lines
            .groupBy { it.productId }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity } }

        requestedByProduct.forEach { (productId, quantity) ->
            // 포트는 "없다"는 사실만 돌려준다. 그것이 실패인지는 여기서 정한다.
            // 상품은 찾았는데 재고 행이 없다면 products와 inventories가 어긋난 것이다.
            // 사용자가 고칠 수 있는 일이 아니므로 OrderException이 아니라 사고로 던진다 → 500.
            val inventory = inventoryRepository.findByProductId(productId)
                ?: error("상품은 있는데 재고 행이 없다: $productId")
            inventoryRepository.updateReserveQuantity(inventory.addReserved(quantity))
        }

        // 검증이 다 끝난 뒤에 쓴다. 재고가 모자라 실패하는 요청은
        // orders / order_lines 에 INSERT를 내지 않는다.
        orderRepository.save(order)

        return PlaceOrder(
            orderId = order.orderId,
            status = order.status,
            totalAmount = order.totalAmount.amount,
            // 3단계에서 선점 행을 만들면 채운다.
            reservations = emptyList(),
        )
    }
}
