package com.commercelab.order

import com.commercelab.bootstrap.CommerceLabApplication
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
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
 * ## 이 테스트가 존재하는 이유
 *
 * 도메인 실패 표현을 `DomainResult`에서 예외로 바꿨다(2026-08-24, M1 §4-2).
 * 바꾼 이유가 롤백이므로, 롤백이 실제로 일어나는지 검사하지 않으면 근거가 증명되지 않는다.
 *
 * ## 2026-08-25 수정 — 단언이 실패할 수 없었다
 *
 * 이전 버전의 `주문건수()`는 이런 쿼리였다.
 *
 * ```sql
 * SELECT count(*) FROM orders
 *  WHERE id IN (SELECT order_id FROM order_lines WHERE product_id = 'p-rollback')
 * ```
 *
 * "상품이 없으면 아무것도 쓰지 않는다" 테스트는 `p-없는상품`을 주문한다.
 * **버그로 주문이 저장되더라도 그 주문의 라인은 `p-없는상품`이므로 이 쿼리는 0을 돌려준다.**
 * 단언이 구조적으로 실패할 수 없었다 — 초록불이지만 아무것도 지키지 않는 테스트였다.
 *
 * 지금은 필터 없이 **테이블 전체를 센다.** 대신 `@BeforeEach`가 orders를 비워
 * "0이어야 한다"가 말이 되게 만든다. 범위를 좁히는 대신 시작점을 고정하는 쪽이다.
 *
 * ## 검증 순서에 기대지 않는다
 *
 * 이전 버전은 `OrderPlacementService`가 **주문을 먼저 저장하고 재고를 나중에 검사하는**
 * 순서에 의존했다. 그 순서는 롤백을 증명하기엔 좋지만 운영 코드로는 낭비다
 * (재고 소진 후 모든 요청이 INSERT하고 롤백된다).
 *
 * `여러 상품 중 하나만 재고가 모자라면...` 테스트는 그 의존을 없앤다.
 * 재고 예약은 상품 단위로 **순차적으로** 일어나므로, 앞 상품을 잡은 뒤 뒤 상품에서
 * 실패하면 검증을 아무리 앞으로 당겨도 부분 쓰기가 생긴다.
 * **서비스가 어떤 순서로 바뀌든 이 테스트는 계속 롤백을 검사한다.**
 *
 * ## 회귀 방지
 *
 * 서비스에서 `try { ... } catch (e: OrderException) { ... }`로 예외를 삼키거나
 * `@Transactional(noRollbackFor = [OrderException::class])`를 붙이면 여기가 빨간불이 된다.
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
        private const val 다른상품 = "p-rollback-2"

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

    /**
     * 테이블 전체를 세는 단언이 성립하려면 시작점이 0이어야 한다.
     *
     * dev/reset은 "그 상품이 걸린 주문"만 지운다. 여기서는 그것으로 부족하다 —
     * 주문이 잘못 저장됐을 때 그 라인이 어느 상품을 가리킬지 모르기 때문이다.
     * 그게 이전 버전이 아무것도 못 잡았던 이유다.
     */
    @BeforeEach
    fun 초기화() {
        재고초기화(상품, total = 10)
        재고초기화(다른상품, total = 10)
        jdbc.update("""DELETE FROM "order".orders""")
    }

    @Test
    fun `재고가 모자라면 409가 나가고 아무것도 남지 않는다`() {
        val status = 주문요청(listOf(상품 to 999))

        assertEquals(409, status, "재고 부족은 비즈니스 실패다. 5xx면 예외 계층이 새고 있다")
        assertEquals(0, 주문건수(), "실패한 주문이 orders에 남았다 — 롤백이 돌지 않았다")
        assertEquals(0, 주문라인건수(), "실패한 주문의 라인이 order_lines에 남았다")
        assertEquals(0, 예약수량(상품), "실패한 주문이 재고를 잡았다")
    }

    @Test
    fun `상품이 없으면 404가 나가고 아무것도 남지 않는다`() {
        val status = 주문요청(listOf("p-없는상품" to 1))

        assertEquals(404, status)
        // 필터 없이 테이블 전체를 센다. 이전 버전은 p-rollback 라인을 가진 주문만 세어서
        // p-없는상품 주문이 저장돼도 잡지 못했다.
        assertEquals(0, 주문건수(), "존재하지 않는 상품 주문이 orders에 남았다")
        assertEquals(0, 주문라인건수())
    }

    @Test
    fun `여러 상품 중 하나만 재고가 모자라면 앞서 잡은 재고도 되돌아간다`() {
        // 첫 라인은 통과하고 두 번째에서 터진다. 재고 예약은 상품 단위로 순차 처리되므로
        // 서비스가 검증을 얼마나 앞으로 당기든 "일부는 쓰고 실패"가 반드시 생긴다.
        // 이 테스트만은 검증 순서에 의존하지 않는다.
        val status = 주문요청(listOf(상품 to 1, 다른상품 to 999))

        assertEquals(409, status)
        assertEquals(
            0, 예약수량(상품),
            "뒤 라인에서 실패했는데 앞 라인이 잡은 재고가 남았다 — 부분 커밋이다",
        )
        assertEquals(0, 예약수량(다른상품))
        assertEquals(0, 주문건수())
        assertEquals(0, 주문라인건수())
    }

    @Test
    fun `성공한 주문은 남는다`() {
        // 대조군. 이게 없으면 "전부 롤백된다"와 "롤백 검사가 통과한다"를 구분할 수 없다 —
        // 주문이 아예 저장되지 않는 버그가 있어도 위 테스트들은 초록불이다.
        val status = 주문요청(listOf(상품 to 3))

        assertEquals(201, status)
        assertEquals(1, 주문건수())
        assertEquals(1, 주문라인건수())
        assertEquals(3, 예약수량(상품))
    }

    @Test
    fun `여러 상품 주문이 전부 성공하면 둘 다 잡힌다`() {
        // 위 대조군의 다중 라인 판. 이게 없으면
        // "다중 라인 주문이 아예 동작하지 않는" 버그도 롤백 테스트를 통과시킨다.
        val status = 주문요청(listOf(상품 to 2, 다른상품 to 4))

        assertEquals(201, status)
        assertEquals(1, 주문건수())
        assertEquals(2, 주문라인건수())
        assertEquals(2, 예약수량(상품))
        assertEquals(4, 예약수량(다른상품))
    }

    // --- 도우미 ---

    private fun 재고초기화(productId: String, total: Int) {
        val body = """{"productId":"$productId","total":$total}"""
        rest.exchange("/api/dev/reset", HttpMethod.POST, HttpEntity(body, json()), String::class.java)
    }

    private fun 주문요청(lines: List<Pair<String, Int>>): Int {
        val linesJson = lines.joinToString(",") { (id, qty) -> """{"productId":"$id","quantity":$qty}""" }
        val body = """{"accountId":"acc-rollback","lines":[$linesJson]}"""
        return rest.exchange("/api/orders", HttpMethod.POST, HttpEntity(body, json()), String::class.java)
            .statusCode.value()
    }

    private fun 주문건수(): Int =
        jdbc.queryForObject("""SELECT count(*) FROM "order".orders""", Int::class.java) ?: 0

    private fun 주문라인건수(): Int =
        jdbc.queryForObject("""SELECT count(*) FROM "order".order_lines""", Int::class.java) ?: 0

    private fun 예약수량(productId: String): Int =
        jdbc.queryForObject(
            """SELECT reserved FROM "order".inventories WHERE product_id = ?""",
            Int::class.java,
            productId,
        ) ?: 0

    private fun json() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
}
