package com.commercelab.order.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface OrderLineRepository : JpaRepository<OrderLineEntity, String> {
}