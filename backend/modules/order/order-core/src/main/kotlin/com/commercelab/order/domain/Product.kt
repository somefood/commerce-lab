package com.commercelab.order.domain

import com.commercelab.common.Money
import java.util.UUID

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val unitAmount: Money,
)
