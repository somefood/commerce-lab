package com.commercelab.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 조립 지점.
 *
 * scanBasePackages를 com.commercelab로 넓히는 이유: order-core와 payment-core의
 * 빈을 여기서 모아 등록하기 위함이다. 모듈은 서로를 모르지만, bootstrap은 전부 안다.
 */
@SpringBootApplication(scanBasePackages = ["com.commercelab"])
class CommerceLabApplication

fun main(args: Array<String>) {
    runApplication<CommerceLabApplication>(*args)
}
