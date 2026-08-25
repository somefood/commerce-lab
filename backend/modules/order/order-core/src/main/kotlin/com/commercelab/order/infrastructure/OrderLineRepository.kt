package com.commercelab.order.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

/**
 * ID 타입이 `Long`이다. 2026-08-25까지 `String`이었다 —
 * `OrderLineEntity.id`는 `Long?`인데 선언이 어긋나 있었다.
 *
 * 지금까지 안 터진 이유는 `saveAll`이 ID 타입을 쓰지 않기 때문이다.
 * `findById`나 `deleteById`를 부르는 순간 `ClassCastException`이 났을 것이다.
 * **컴파일러가 못 잡는 종류의 불일치라 쓰기 시작할 때까지 조용하다.**
 */
interface OrderLineRepository : JpaRepository<OrderLineEntity, Long>
