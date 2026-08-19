package com.commercelab.order

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import org.junit.jupiter.api.Disabled
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
 * M1의 핵심 스펙. 실제 Postgres 컨테이너에 붙어 동시 주문을 던진다.
 *
 * 단계별로 기대가 다르다.
 *   [1단계] `동시 100요청에서 재고 50개를 초과 판매하지 않는다`가 실패한다. 그 실패가 관측 결과다.
 *           실패 메시지의 성공 건수 - 50 이 오버셀 건수다. M1 문서 §8 표에 적을 것.
 *   [2단계] 위 테스트가 통과해야 한다. @Disabled가 붙은 충돌 테스트를 켠다.
 *   [3단계] 나머지 @Disabled를 켠다.
 *
 * H2가 아니라 Postgres인 이유: SELECT FOR UPDATE, 조건부 UPDATE, 격리 수준의 동작이
 * DB마다 다르다. 테스트가 프로덕션과 다른 DB를 쓰면 락 테스트는 전부 거짓 안심이 된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class ConcurrentOrderIntegrationTest {

    companion object {
        private const val 상품 = "p-sneaker"
        private const val 재고 = 50
        private const val 동시요청 = 100

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
     * 재고 50에 동시 100요청. 성공은 정확히 50건이어야 한다.
     *
     * [1단계] 실패가 정상이다. 락 없이 조회 후 검사하고 갱신하면 그 사이가 벌어진다.
     */
    @Test
    fun `동시 100요청에서 재고 50개를 초과 판매하지 않는다`() {
        재고초기화(재고)

        val 출발준비 = CountDownLatch(동시요청)
        val 출발신호 = CountDownLatch(1)
        val 성공 = AtomicInteger()
        val 서버오류 = AtomicInteger()

        val pool = Executors.newFixedThreadPool(동시요청)
        repeat(동시요청) { i ->
            pool.submit {
                출발준비.countDown()
                출발신호.await()
                val status = 주문요청("acc-$i", 상품, 1)
                when {
                    status in 200..299 -> 성공.incrementAndGet()
                    status >= 500 -> 서버오류.incrementAndGet()
                }
            }
        }

        // 스레드가 실제로 동시에 출발해야 경합이 재현된다.
        // 이 래치가 없으면 앞선 요청이 이미 커밋을 끝낸 뒤 다음 요청이 시작될 수 있다.
        출발준비.await(30, TimeUnit.SECONDS)
        출발신호.countDown()
        pool.shutdown()
        pool.awaitTermination(120, TimeUnit.SECONDS)

        val 예약된수량 = 예약수량조회(상품)

        assertEquals(
            0, 서버오류.get(),
            "재고 부족은 비즈니스 실패다. 5xx로 나가면 도메인 에러가 예외로 새고 있다는 뜻이다",
        )
        assertEquals(
            재고, 성공.get(),
            "성공 건수가 재고를 넘으면 오버셀이다. 초과분 = ${성공.get() - 재고}건",
        )
        // 애플리케이션 카운터를 믿지 않는다. DB의 실제 값으로 다시 확인한다.
        assertEquals(재고, 예약된수량, "DB의 reserved가 재고를 넘었다")
    }

    @Test
    @Disabled("2단계에서 활성화한다")
    fun `충돌이 재시도 한도를 넘으면 5xx가 아니라 명시적 에러로 끝난다`() {
        // given: 재시도 한도를 넘길 만큼 높은 경합
        // when : 동시 주문
        // then : 실패 응답의 본문이 ConflictExhausted를 가리키고, 상태 코드는 409다
        //        재시도로 흡수된 요청과 한도를 넘긴 요청의 비율을 기록할 것 (§8 표)
    }

    @Test
    @Disabled("3단계에서 활성화한다")
    fun `만료 처리와 확정 처리는 서로를 배제한다`() {
        // given: expires_at 이 곧 지나는 HELD 예약 하나
        // when : releaseExpired(now) 와 confirm(orderId) 를 동시에 실행
        // then : 정확히 하나만 성공한다
        //        reservations.status 는 EXPIRED 또는 CONFIRMED 중 하나이고,
        //        inventories.reserved 가 그 결과와 일관돼야 한다
        //        (확정됐는데 재고가 반환됐거나, 만료됐는데 재고가 잠긴 채면 실패)
    }

    @Test
    @Disabled("3단계에서 활성화한다")
    fun `상품 순서가 엇갈린 동시 주문에서 데드락이 나지 않는다`() {
        // given: 상품 두 개
        // when : A는 [p1, p2] 순서로, B는 [p2, p1] 순서로 동시에 주문
        // then : 둘 다 정상 응답. 데드락이면 타임아웃이나 DeadlockLoserDataAccessException으로 실패한다
    }

    @Test
    @Disabled("3단계에서 활성화한다")
    fun `만료된 선점은 재고를 반환한다`() {
        // given: 재고를 전부 선점해 available 이 0인 상태
        // when : TTL 이 지나고 만료 배치가 돈 뒤
        // then : available 이 원래대로 돌아오고, 새 주문이 성공한다
    }

    // --- 도우미 ---

    private fun 재고초기화(total: Int) {
        val body = """{"productId":"$상품","total":$total}"""
        rest.exchange("/api/dev/reset", HttpMethod.POST, HttpEntity(body, json()), String::class.java)
    }

    private fun 주문요청(accountId: String, productId: String, quantity: Int): Int {
        val body = """{"accountId":"$accountId","lines":[{"productId":"$productId","quantity":$quantity}]}"""
        val response = rest.exchange("/api/orders", HttpMethod.POST, HttpEntity(body, json()), String::class.java)
        return response.statusCode.value()
    }

    private fun 예약수량조회(productId: String): Int =
        jdbc.queryForObject(
            """SELECT reserved FROM "order".inventories WHERE product_id = ?""",
            Int::class.java,
            productId,
        ) ?: 0

    private fun json() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
}
