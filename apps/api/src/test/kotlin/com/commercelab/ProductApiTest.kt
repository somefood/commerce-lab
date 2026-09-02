package com.commercelab

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration::class)
class ProductApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `상품을 등록하고 조회한다`() {
        val result = mockMvc.perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"티셔츠","price":29000,"stockQuantity":10}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("티셔츠"))
            .andReturn()

        val location = result.response.getHeader("Location")
        mockMvc.perform(get(location!!)).andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `CUSTOMER는 상품을 등록할 수 없다`() {
        mockMvc.perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"티셔츠","price":29000,"stockQuantity":10}""")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `존재하지 않은 상품을 검색할 수 없다`() {
        mockMvc.perform(
            get("/api/products/1000")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `상품등록 시 음수는 입력되면 안된다`() {
        mockMvc.perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"티셔츠","price":29000,"stockQuantity":-10}""")
        )
            .andExpect(status().isBadRequest)
            .andReturn()
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `상품명이 100자를 넘으면 등록할 수 없다`() {
        val longName = "가".repeat(101)

        mockMvc.perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$longName","price":29000,"stockQuantity":10}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `재고가 부족하면 차감할 수 없다`() {
        val result = mockMvc.perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"상품1","price":29000,"stockQuantity":10}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val location = result.response.getHeader("Location")!!
        val id = location.substringAfterLast("/")

        mockMvc.perform(
            post("/api/products/$id/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"quantity":-11}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("수량은 0 미만이 될 수 없습니다. 현재 수량=10"))
    }

    @Test
    @WithMockUser(roles = ["CUSTOMER"])
    fun `CUSTOMER는 상품을 삭제할 수 없다`() {
        mockMvc.perform(
            delete("/api/products/1")
        )
            .andExpect(status().isForbidden)
    }
}