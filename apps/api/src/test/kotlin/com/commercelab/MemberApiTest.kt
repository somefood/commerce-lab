package com.commercelab

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `회원을 등록한다`() {
        mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"register-test@a.com","password":"123456678","name":"주석홍"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("주석홍"))
    }

    @Test
    fun `중복된 이메일로 가입할 수 없다`() {
        mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"duplicate-test@a.com" ,"password":"123456678","name":"주석홍"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("주석홍"))

        mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"duplicate-test@a.com" ,"password":"123456678","name":"주석홍"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("중복된 이메일입니다."))
    }
}