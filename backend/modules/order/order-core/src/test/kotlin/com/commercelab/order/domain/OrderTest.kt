package com.commercelab.order.domain

import com.commercelab.common.Money
import com.commercelab.common.errorOrNull
import com.commercelab.common.getOrNull
import com.commercelab.common.isFailure
import com.commercelab.order.api.OrderError
import com.commercelab.order.api.OrderStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 주문 애그리거트의 실행 가능한 스펙이다. 스프링 없이 순수 단위 테스트로 돈다.
 *
 * 이 테스트가 가정하는 도메인 API는 다음과 같다. 시그니처가 마음에 들지 않으면
 * 바꾸고 이 테스트도 함께 고치면 된다 — 스펙은 "무엇을 보장하는가"이지
 * "어떤 이름을 쓰는가"가 아니다.
 *
 *   class Order {
 *       companion object {
 *           fun place(orderId: String, accountId: String, lines: List<OrderLine>, now: Instant)
 *               : DomainResult<OrderError, Order>
 *       }
 *       val orderId: String
 *       val status: OrderStatus
 *       val totalAmount: Money
 *       fun markPaid(now: Instant): DomainResult<OrderError, Order>
 *       fun cancel(now: Instant): DomainResult<OrderError, Order>
 *   }
 *
 *   data class OrderLine(val productId: String, val quantity: Int, val unitAmount: Money)
 *
 * 상태 전이 실패를 표현할 에러가 아직 OrderError에 없다. 아래 테스트는
 * OrderError.InvalidStatusTransition(from, to)를 요구한다. order-api에 추가할 것.
 *
 * Instant를 파라미터로 받는 이유: 시간을 애플리케이션이 소유한다는 결정
 * (M1 문서 §3, updated_at 논의)을 도메인에도 일관되게 적용한다.
 * Instant.now()를 도메인 안에서 부르면 3단계 만료 테스트에서 시간을 고정할 수 없다.
 */
class OrderTest {

    private val 지금 = Instant.parse("2026-08-19T10:00:00Z")

    /** 상태 전이가 일어나는 시각. 지금과 달라야 "갱신됐는지"를 판별할 수 있다. */
    private val 나중 = Instant.parse("2026-08-19T11:30:00Z")

    private fun 주문라인(productId: String, quantity: Int, unitAmount: Long) =
        OrderLine(productId = productId, quantity = quantity, unitAmount = Money.of(unitAmount))

    @Test
    fun `주문 총액은 라인 금액의 합이다`() {
        val result = Order.place(
            orderId = "ord-1",
            accountId = "acc-1",
            lines = listOf(
                주문라인("p-1", quantity = 2, unitAmount = 1_000),
                주문라인("p-2", quantity = 1, unitAmount = 2_500),
            ),
            now = 지금,
        )

        val order = result.getOrNull()
        assertEquals(Money.of(4_500), order?.totalAmount)
    }

    @Test
    fun `수량이 0 이하인 라인은 주문을 만들 수 없다`() {
        val result = Order.place(
            orderId = "ord-1",
            accountId = "acc-1",
            lines = listOf(주문라인("p-1", quantity = 0, unitAmount = 1_000)),
            now = 지금,
        )

        assertTrue(result.isFailure, "수량 0은 거부되어야 한다")
        assertEquals(OrderError.InvalidQuantity("p-1", 0), result.errorOrNull())
    }

    @Test
    fun `라인 중 하나만 수량이 0이어도 주문을 만들 수 없다`() {
        // 정상 라인과 잘못된 라인이 섞여 있다. "전부 잘못된 경우"만 막으면 이 주문이 통과한다.
        val result = Order.place(
            orderId = "ord-1",
            accountId = "acc-1",
            lines = listOf(
                주문라인("p-1", quantity = 2, unitAmount = 1_000),
                주문라인("p-2", quantity = 0, unitAmount = 2_000),
            ),
            now = 지금,
        )

        assertTrue(result.isFailure, "라인 하나라도 수량이 0 이하면 주문 전체가 거부되어야 한다")
    }

    @Test
    fun `수량 오류는 문제가 된 라인을 가리킨다`() {
        // 에러가 어느 라인 때문인지 알려주지 않으면 호출자는 무엇을 고쳐야 할지 모른다.
        val result = Order.place(
            orderId = "ord-1",
            accountId = "acc-1",
            lines = listOf(
                주문라인("p-1", quantity = 1, unitAmount = 1_000),
                주문라인("p-2", quantity = -3, unitAmount = 2_000),
            ),
            now = 지금,
        )

        assertEquals(OrderError.InvalidQuantity("p-2", -3), result.errorOrNull())
    }

    @Test
    fun `빈 주문의 실패 이유는 수량이나 선점과 구분된다`() {
        // 빈 주문은 "라인이 없다"는 사건이다. 다른 실패 케이스를 빌려 쓰면
        // API 사용자는 무엇을 고쳐야 할지 알 수 없다.
        // OrderError에 이 경우를 위한 케이스가 필요하다 (예: EmptyOrder).
        val result = Order.place(
            orderId = "ord-1",
            accountId = "acc-1",
            lines = emptyList(),
            now = 지금,
        )

        val error = result.errorOrNull()
        assertTrue(
            error !is OrderError.ReservationAlreadySettled,
            "빈 주문과 '선점이 이미 처리됨'은 다른 사건이다",
        )
        assertTrue(
            error !is OrderError.InvalidQuantity,
            "라인이 없는 것과 라인의 수량이 잘못된 것은 다른 사건이다",
        )
    }

    @Test
    fun `상태 전이 실패는 실제 현재 상태를 알려준다`() {
        // 에러에 담긴 from이 사실과 다르면, 로그를 보는 사람이 잘못된 원인을 쫓게 된다.
        val paid = 주문생성().markPaid(지금).getOrNull()!!

        val result = paid.markPaid(지금)

        assertTrue(result.isFailure, "이미 결제된 주문을 다시 결제할 수 없다")
        assertEquals(
            OrderError.InvalidStatusTransition(OrderStatus.PAID, OrderStatus.PAID),
            result.errorOrNull(),
            "from은 호출 시점의 실제 상태여야 한다",
        )
    }

    @Test
    fun `라인이 하나도 없는 주문은 만들 수 없다`() {
        val result = Order.place(
            orderId = "ord-1",
            accountId = "acc-1",
            lines = emptyList(),
            now = 지금,
        )

        assertTrue(result.isFailure, "빈 주문은 거부되어야 한다")
    }

    @Test
    fun `생성된 주문은 CREATED 상태다`() {
        val order = Order.place(
            orderId = "ord-1",
            accountId = "acc-1",
            lines = listOf(주문라인("p-1", 1, 1_000)),
            now = 지금,
        ).getOrNull()

        assertEquals(OrderStatus.CREATED, order?.status)
    }

    @Test
    fun `CREATED 주문만 PAID로 갈 수 있다`() {
        val created = 주문생성()

        val paid = created.markPaid(지금).getOrNull()

        assertEquals(OrderStatus.PAID, paid?.status)
    }

    @Test
    fun `취소된 주문은 PAID로 갈 수 없다`() {
        val cancelled = 주문생성().cancel(지금).getOrNull()!!

        val result = cancelled.markPaid(지금)

        assertTrue(result.isFailure, "CANCELLED에서 PAID로 가면 결제는 됐는데 재고는 남에게 넘어간 상태가 된다")
        assertEquals(
            OrderError.InvalidStatusTransition(OrderStatus.CANCELLED, OrderStatus.PAID),
            result.errorOrNull(),
        )
    }

    @Test
    fun `이미 취소된 주문은 다시 취소할 수 없다`() {
        // 멱등이 아니라 실패로 정한다. 두 번째 취소 요청은 호출자가 상태를 잘못 알고 있다는 신호이고,
        // 조용히 성공시키면 그 오해가 드러나지 않는다.
        val cancelled = 주문생성().cancel(지금).getOrNull()!!

        val result = cancelled.cancel(지금)

        assertTrue(result.isFailure)
        assertEquals(
            OrderError.InvalidStatusTransition(OrderStatus.CANCELLED, OrderStatus.CANCELLED),
            result.errorOrNull(),
        )
    }

    @Test
    fun `결제된 주문도 취소할 수 있다`() {
        // 설계문서 §4.2: PAID --payment.failed--> CANCELLED 경로가 있다
        val paid = 주문생성().markPaid(지금).getOrNull()!!

        val result = paid.cancel(지금)

        assertEquals(OrderStatus.CANCELLED, result.getOrNull()?.status)
    }

    @Test
    fun `주문을 만들면 placedAt과 updatedAt이 같다`() {
        val order = 주문생성()

        assertEquals(지금, order.placedAt)
        assertEquals(지금, order.updatedAt, "생성 시점에는 마지막 변경이 곧 생성이다")
    }

    @Test
    fun `결제하면 updatedAt은 갱신되고 placedAt은 그대로다`() {
        val paid = 주문생성().markPaid(나중).getOrNull()!!

        assertEquals(나중, paid.updatedAt, "상태가 바뀌면 updated_at도 그 시각으로 움직여야 한다")
        assertEquals(지금, paid.placedAt, "주문한 시각은 사후에 바뀌지 않는다")
    }

    @Test
    fun `CREATED 주문을 취소하면 updatedAt이 취소 시각으로 갱신된다`() {
        val cancelled = 주문생성().cancel(나중).getOrNull()!!

        assertEquals(나중, cancelled.updatedAt)
        assertEquals(지금, cancelled.placedAt)
    }

    @Test
    fun `PAID 주문을 취소해도 updatedAt이 갱신된다`() {
        // CREATED와 PAID는 cancel 안에서 서로 다른 분기를 탄다.
        // 한쪽 분기만 now를 반영하는 실수가 실제로 두 번 있었다. 두 경로를 따로 고정한다.
        val paid = 주문생성().markPaid(지금).getOrNull()!!

        val cancelled = paid.cancel(나중).getOrNull()!!

        assertEquals(나중, cancelled.updatedAt, "결제 경로로 들어온 취소도 시각을 남겨야 한다")
    }

    @Test
    fun `전이에 실패해도 원본 주문은 그대로다`() {
        // 실패는 새 Order를 만들지 않는다. 원본이 조용히 바뀌면 호출자는 어느 값을 믿어야 할지 모른다.
        val cancelled = 주문생성().cancel(지금).getOrNull()!!

        val result = cancelled.markPaid(나중)

        assertTrue(result.isFailure)
        assertEquals(OrderStatus.CANCELLED, cancelled.status)
        assertEquals(지금, cancelled.updatedAt, "실패한 전이가 원본의 시각을 건드리면 안 된다")
    }

    private fun 주문생성(): Order =
        Order.place(
            orderId = "ord-1",
            accountId = "acc-1",
            lines = listOf(주문라인("p-1", 1, 1_000)),
            now = 지금,
        ).getOrNull() ?: error("주문 생성이 실패하면 이 테스트는 의미가 없다")
}