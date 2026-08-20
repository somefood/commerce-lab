package com.commercelab.bootstrap.web.order

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ProductController {

    @GetMapping("/api/products")
    fun getProducts() {

    }
}