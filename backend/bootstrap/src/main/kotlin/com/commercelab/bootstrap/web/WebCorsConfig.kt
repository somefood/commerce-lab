package com.commercelab.bootstrap.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 프론트(3000)와 백엔드(8080)는 다른 오리진이다. 브라우저는 응답에
 * Access-Control-Allow-Origin이 없으면 fetch 결과를 읽지 못한다.
 *
 * 허용 오리진을 와일드카드가 아니라 설정값으로 두는 이유: 운영에서
 * 실수로 모든 오리진이 열리는 것을 막고, 환경마다 명시적으로 정하게 한다.
 */
@Configuration
class WebCorsConfig(
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
            .allowedHeaders("*")
    }
}
