package com.commercelab

import org.hamcrest.core.StringStartsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `로그인을 하면 토큰이 발급된다`() {
        mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"register-test@a.com","password":"12345678","name":"주석홍"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("주석홍"))

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"register-test@a.com","password":"12345678"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `유효하지 않은 비밀번호로 로그인 하면 실패한다`() {
        mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"register-test@a.com","password":"12345678","name":"주석홍"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("주석홍"))

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"register-test@a.com","password":"87654321"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
    }
}