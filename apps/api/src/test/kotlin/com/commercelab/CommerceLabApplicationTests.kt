package com.commercelab

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * 스모크 테스트: 스프링 컨텍스트가 정상적으로 뜨는지만 확인한다.
 * 빈 설정이 꼬이면 이 테스트가 가장 먼저 깨진다.
 */
@SpringBootTest
class CommerceLabApplicationTests {

    @Test
    fun contextLoads() {
    }
}
