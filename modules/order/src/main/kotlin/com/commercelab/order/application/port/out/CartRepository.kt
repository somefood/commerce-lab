package com.commercelab.order.application.port.out

import com.commercelab.order.domain.Cart

interface CartRepository {

    fun findByMemberId(memberId: Long): Cart?
}