package com.commercelab

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
class MemberApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `회원을 등록한다`() {
        val result = mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"a@a.com" ,"password":"123456678","name":"주석홍"}""")
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("주석홍"))
            .andReturn()
    }
}