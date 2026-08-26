package com.commercelab.product.adapter.`in`.web

import com.commercelab.product.application.port.`in`.DeleteProductUseCase
import com.commercelab.product.application.port.`in`.EditProductUseCase
import com.commercelab.product.application.port.`in`.GetProductQuery
import com.commercelab.product.application.port.`in`.RegisterProductUseCase
import com.commercelab.product.domain.Product
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
class ProductController(
    private val registerProductUseCase: RegisterProductUseCase,
    private val getProductQuery: GetProductQuery,
    private val editProductUseCase: EditProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
) {

    @PostMapping("/api/products")
    fun createProduct(@RequestBody createRequest: ProductCreateRequest): ResponseEntity<Nothing> {
        val product = registerProductUseCase.registerProduct(createRequest)
        return ResponseEntity
            .created(URI("/api/products/${product.id}"))
            .build()
    }

    @GetMapping("/api/products/{id}")
    fun getProduct(@PathVariable("id") id: Long): ResponseEntity<Product> {
        val product = getProductQuery.getProduct(id)
        return ResponseEntity.ok(product)
    }

    @GetMapping("/api/products")
    fun getProducts(): ResponseEntity<List<Product>> {
        val products = getProductQuery.getAllProducts()
        return ResponseEntity.ok(products)
    }

    @PutMapping("/api/products/{id}")
    fun editProduct(@PathVariable id: Long, @RequestBody editRequest: ProductEditRequest): ResponseEntity<Nothing> {
        editProductUseCase.editProduct(id, editRequest)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/api/products/{id}")
    fun deleteProduct(@PathVariable id: Long): ResponseEntity<Nothing> {
        deleteProductUseCase.deleteProduct(id)
        return ResponseEntity.noContent().build()
    }
}
