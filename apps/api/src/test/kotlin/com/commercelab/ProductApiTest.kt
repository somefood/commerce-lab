package com.commercelab

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
class ProductApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
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
    fun `상품등록 시 음수는 입력되면 안된다`() {
        mockMvc.perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"티셔츠","price":29000,"stockQuantity":-10}""")
        )
            .andExpect(status().isBadRequest)
            .andReturn()
    }
}