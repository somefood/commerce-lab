package com.commercelab

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * 인증 파이프라인 왕복 테스트.
 *
 * ProductApiTest의 @WithMockUser가 "인가 규칙"만 검증한다면, 여기서는 진짜 토큰이
 * 발급 → 서명 검증 → 클레임-권한 변환 → 인가까지 전 구간을 통과하는지 검증한다.
 * 이 파일이 있어야 JwtTokenIssuer / jwtAuthenticationConverter가 심판대에 오른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration::class)
class AuthFlowApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    // 시더와 같은 프로퍼티를 바라본다 — 값이 바뀌어도 테스트와 시더가 함께 움직인다
    @Value("\${app.admin.email}")
    lateinit var adminEmail: String

    @Value("\${app.admin.password}")
    lateinit var adminPassword: String

    @Test
    fun `ADMIN 토큰으로 상품을 등록할 수 있다`() {
        val token = login(adminEmail, adminPassword)

        mockMvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"관리자상품","price":10000,"stockQuantity":5}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("관리자상품"))
    }

    @Test
    fun `CUSTOMER 토큰으로는 상품을 등록할 수 없다`() {
        registerMember("flow-customer@a.com")
        val token = login("flow-customer@a.com")

        mockMvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"고객상품","price":10000,"stockQuantity":5}""")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `자기 정보를 조회할 수 있다`() {
        registerMember("flow-me@a.com", name = "왕복테스터")
        val token = login("flow-me@a.com")

        mockMvc.perform(
            get("/api/members/me")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("flow-me@a.com"))
            .andExpect(jsonPath("$.name").value("왕복테스터"))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
    }

    @Test
    fun `토큰 없이 자기 정보를 조회하면 401이다`() {
        mockMvc.perform(get("/api/members/me"))
            .andExpect(status().isUnauthorized)
    }

    // ── 셋업 헬퍼: 배경은 숨기고 검증은 테스트 본문에 남긴다 ──────────────

    private fun registerMember(email: String, password: String = "12345678", name: String = "테스터") {
        mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password","name":"$name"}""")
        )
            .andExpect(status().isCreated)
    }

    private fun login(email: String, password: String = "12345678"): String {
        val body = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
            .response.contentAsString

        return objectMapper.readValue(body, Map::class.java)["accessToken"] as String
    }
}
