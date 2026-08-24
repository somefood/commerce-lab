package com.commercelab.order

import com.commercelab.bootstrap.CommerceLabApplication
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * **실패한 주문이 DB에 아무것도 남기지 않는지** 검사한다.
 *
 * ## 이 테스트가 존재하는 이유 (2026-08-24)
 *
 * 도메인 실패 표현을 `DomainResult`에서 예외로 바꿨다. 바꾼 이유가 롤백이므로,
 * 롤백이 실제로 일어나는지 검사하지 않으면 전환의 근거가 증명되지 않는다.
 *
 * `OrderPlacementService.place()`는 **주문을 먼저 저장하고 재고를 나중에 검사한다.**
 * 일부러 그 순서다. 재고가 모자라면 `OutOfStock`이 던져지고 `@Transactional`이
 * 이미 저장한 orders/order_lines 행까지 되돌린다.
 *
 * 값 방식이었다면 이 순서에서 **실패했다고 응답해놓고 주문 행은 남는다.**
 * 스프링은 예외를 보고 롤백을 결정하고 반환값은 보지 않기 때문이다.
 * (실측했던 증상: 성공 1건인데 orders=2)
 *
 * 그래서 이 테스트는 두 가지를 동시에 못 박는다.
 *   1. 재고 부족이 5xx가 아니라 409로 나간다 (예상된 실패는 사고가 아니다)
 *   2. 실패한 요청은 DB에 흔적을 남기지 않는다 (롤백이 실제로 돈다)
 *
 * 1번만 검사하면 롤백이 깨져도 초록불이 켜진다. 응답 코드는 롤백 여부를 모른다.
 *
 * ## 회귀 방지
 *
 * 나중에 누가 서비스에서 `try { ... } catch (e: OrderException) { ... }`로 예외를 삼키거나,
 * `@Transactional(noRollbackFor = [OrderException::class])`를 붙이면 여기가 빨간불이 된다.
 * 그게 이 테스트의 수명이 긴 이유다.
 */
@SpringBootTest(
    classes = [CommerceLabApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("dev")
@Testcontainers
class OrderRollbackIntegrationTest {

    companion object {
        private const val 상품 = "p-rollback"

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("commerce")
            .withUsername("commerce")
            .withPassword("commerce")
            .withInitScript("db/init-schemas.sql")

        @DynamicPropertySource
        @JvmStatic
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `재고가 모자라면 409가 나가고 주문 행은 남지 않는다`() {
        재고초기화(total = 1)

        val status = 주문요청("acc-rollback", quantity = 5)

        assertEquals(409, status, "재고 부족은 비즈니스 실패다. 5xx면 예외 계층이 새고 있다")
        assertEquals(0, 주문건수(), "실패한 주문이 orders에 남았다 — 롤백이 돌지 않았다")
        assertEquals(0, 주문라인건수(), "실패한 주문의 라인이 order_lines에 남았다")
        assertEquals(0, 예약수량(), "실패한 주문이 재고를 잡았다")
    }

    @Test
    fun `상품이 없으면 404가 나가고 아무것도 쓰지 않는다`() {
        재고초기화(total = 10)

        val status = 주문요청("acc-rollback", quantity = 1, productId = "p-없는상품")

        assertEquals(404, status)
        assertEquals(0, 주문건수())
    }

    @Test
    fun `성공한 주문은 남는다`() {
        // 대조군. 이게 없으면 "전부 롤백된다"와 "롤백 검사가 통과한다"를 구분할 수 없다 —
        // 주문이 아예 저장되지 않는 버그가 있어도 위 테스트들은 초록불이다.
        재고초기화(total = 10)

        val status = 주문요청("acc-rollback", quantity = 3)

        assertEquals(201, status)
        assertEquals(1, 주문건수())
        assertEquals(1, 주문라인건수())
        assertEquals(3, 예약수량())
    }

    // --- 도우미 ---

    private fun 재고초기화(total: Int) {
        val body = """{"productId":"$상품","total":$total}"""
        rest.exchange("/api/dev/reset", HttpMethod.POST, HttpEntity(body, json()), String::class.java)
    }

    private fun 주문요청(accountId: String, quantity: Int, productId: String = 상품): Int {
        val body = """{"accountId":"$accountId","lines":[{"productId":"$productId","quantity":$quantity}]}"""
        return rest.exchange("/api/orders", HttpMethod.POST, HttpEntity(body, json()), String::class.java)
            .statusCode.value()
    }

    /** 이 상품이 걸린 주문만 센다. dev reset이 지우는 범위와 같아야 한다. */
    private fun 주문건수(): Int =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM "order".orders
             WHERE id IN (SELECT order_id FROM "order".order_lines WHERE product_id = ?)
            """.trimIndent(),
            Int::class.java,
            상품,
        ) ?: 0

    private fun 주문라인건수(): Int =
        jdbc.queryForObject(
            """SELECT count(*) FROM "order".order_lines WHERE product_id = ?""",
            Int::class.java,
            상품,
        ) ?: 0

    private fun 예약수량(): Int =
        jdbc.queryForObject(
            """SELECT reserved FROM "order".inventories WHERE product_id = ?""",
            Int::class.java,
            상품,
        ) ?: 0

    private fun json() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
}
