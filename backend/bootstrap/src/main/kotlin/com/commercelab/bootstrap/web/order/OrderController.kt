package com.commercelab.bootstrap.web.order

import com.commercelab.order.api.OrderPlacement
import com.commercelab.order.api.PlaceOrderCommand
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class OrderController(
    private val orderPlacement: OrderPlacement
) {

    @PostMapping("/api/orders")
    fun place(@RequestBody command: PlaceOrderCommand): ResponseEntity<*> {
        TODO()
    }

    @GetMapping("/api/orders/{orderId}")
    fun getOrder(@PathVariable orderId: String): ResponseEntity<*> {
        TODO()
    }

    @PostMapping("/api/orders/{orderId}/confirm")
    fun confirmOrder(@PathVariable orderId: String): ResponseEntity<*> {
        TODO()
    }
}