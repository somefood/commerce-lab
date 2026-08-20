package com.commercelab.order.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface InventoryJpaRepository : JpaRepository<InventoryEntity, String> {

}