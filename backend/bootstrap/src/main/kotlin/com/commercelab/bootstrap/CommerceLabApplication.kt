package com.commercelab.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * 조립 지점.
 *
 * scanBasePackages를 com.commercelab로 넓히는 이유: order-core와 payment-core의
 * 빈을 여기서 모아 등록하기 위함이다. 모듈은 서로를 모르지만, bootstrap은 전부 안다.
 *
 * @EntityScan / @EnableJpaRepositories를 따로 붙이는 이유:
 * scanBasePackages는 @ComponentScan에만 적용된다. 엔티티와 JPA 리포지터리는
 * "자동 설정 패키지"를 기준으로 찾는데, 그 기준은 이 클래스가 놓인 패키지
 * (com.commercelab.bootstrap)로 고정된다. 모듈은 그 하위가 아니므로 영영 발견되지 않는다.
 * 실제로 이 두 줄이 없으면 기동 로그에 "Found 0 JPA repository interfaces"가 찍히고,
 * ddl-auto=validate는 검사할 엔티티가 없어 조용히 통과한다.
 */
@SpringBootApplication(scanBasePackages = ["com.commercelab"])
@EntityScan("com.commercelab")
@EnableJpaRepositories("com.commercelab")
class CommerceLabApplication

fun main(args: Array<String>) {
    runApplication<CommerceLabApplication>(*args)
}
