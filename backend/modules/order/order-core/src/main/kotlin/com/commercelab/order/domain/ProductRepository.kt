package com.commercelab.order.domain

interface ProductRepository {

    fun findByIds(ids: List<String>): List<Product>
}