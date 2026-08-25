package com.commercelab.bootstrap.config

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 시간을 빈으로 만든다.
 *
 * 왜: `Instant.now()`를 코드 안에서 직접 부르면 그 시각을 테스트가 고를 수 없다.
 * 3단계 선점 만료는 "만료 3초 전", "만료 직후" 같은 시점을 만들어야 하는데,
 * 실제 시계를 쓰면 `Thread.sleep`으로 기다리거나 TTL을 비현실적으로 줄여야 한다.
 * 둘 다 느리고 불안정한 테스트를 만든다.
 *
 * 이 빈을 바꿔치기하면 테스트가 시간을 고정할 수 있다.
 * ```kotlin
 * @TestConfiguration
 * class 고정시계 {
 *     @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC)
 * }
 * ```
 *
 * `systemUTC()`인 이유: 저장하는 값이 `Instant`(시간대 없는 절대 시각)다.
 * 서버 시간대에 기대면 배포 환경이 바뀔 때 만료 시각이 조용히 어긋난다.
 * 시간대는 화면에 보여줄 때만 필요하고, 그건 프론트의 몫이다.
 *
 * bootstrap에 두는 이유: 무엇을 주입할지 정하는 것은 조립 지점의 일이다.
 * order-core가 자기 시계를 직접 만들면 모듈이 시간 정책을 소유하게 되고,
 * 테스트에서 바꿔 끼울 자리가 사라진다.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
