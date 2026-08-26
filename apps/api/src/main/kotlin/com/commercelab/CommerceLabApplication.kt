package com.commercelab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 애플리케이션 진입점.
 *
 * 이 클래스가 com.commercelab 패키지 "루트"에 있는 것이 중요하다:
 * @SpringBootApplication은 자기 패키지의 하위 패키지를 전부 스캔하므로,
 * com.commercelab.product, com.commercelab.order 등 모든 도메인 모듈의
 * 빈과 엔티티가 별도 설정 없이 자동으로 등록된다.
 */
@SpringBootApplication
class CommerceLabApplication

fun main(args: Array<String>) {
    // Kotlin에는 클래스 밖에 함수를 쓸 수 있다 (top-level function).
    // runApplication<T>(...)는 SpringApplication.run(T::class.java, ...)의
    // Kotlin 확장 함수 버전. *args는 배열을 가변인자로 펼치는 스프레드 연산자.
    runApplication<CommerceLabApplication>(*args)
}
