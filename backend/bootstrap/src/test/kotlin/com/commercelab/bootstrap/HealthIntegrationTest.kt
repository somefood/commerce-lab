package com.commercelab.bootstrap

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * H2가 아니라 실제 Postgres 컨테이너에 붙는다.
 *
 * 이유: M1에서 다룰 SELECT FOR UPDATE, advisory lock, 격리 수준은 H2에서
 * 동작이 다르거나 아예 없다. 테스트가 프로덕션과 다른 DB를 쓰면 락 관련
 * 테스트는 전부 거짓 안심이 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HealthIntegrationTest {

    companion object {
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
    lateinit var mockMvc: MockMvc

    @Test
    fun `헬스 엔드포인트가 UP을 반환한다`() {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("commerce-lab"))
    }

    @Test
    fun `OpenAPI 문서가 생성된다`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/health']").exists())
    }

    @Test
    fun `order와 payment 스키마가 존재한다`() {
        postgres.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                val rs = statement.executeQuery(
                    "SELECT schema_name FROM information_schema.schemata " +
                        "WHERE schema_name IN ('order', 'payment') ORDER BY schema_name"
                )
                val schemas = mutableListOf<String>()
                while (rs.next()) {
                    schemas.add(rs.getString(1))
                }
                // kotlin의 assert()는 -ea 옵션이 꺼져 있으면 통째로 무시된다.
                // 테스트에서는 항상 assertEquals를 쓴다.
                assertEquals(listOf("order", "payment"), schemas)
            }
        }
    }
}
