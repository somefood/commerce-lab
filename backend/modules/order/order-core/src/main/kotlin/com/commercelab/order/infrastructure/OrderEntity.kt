package com.commercelab.order.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.time.Instant
import org.springframework.data.domain.Persistable

/**
 * `Persistable`을 구현하는 이유 — INSERT 앞에 붙던 SELECT를 없앤다 (2026-08-25).
 *
 * `orders.id`는 애플리케이션이 만든다(ADR 대기 항목, M1 §3). 즉 `save()`를 부르는 시점에
 * `@Id`가 이미 채워져 있다. Spring Data의 기본 판정은 **"id가 null이면 새 것, 아니면 기존 것"**
 * 이라 이 엔티티를 항상 기존 것으로 보고 `merge()`를 부른다.
 * `merge()`는 "DB에 이미 있나" 확인하려고 **INSERT 앞에 SELECT를 한 번 더 낸다.**
 *
 * 실측(`logging.level.org.hibernate.SQL=DEBUG`, 주문 1건):
 * ```
 * 이전:  select products → select orders ← 이것 → insert orders → insert order_lines
 * 이후:  select products → insert orders → insert order_lines
 * ```
 *
 * 주문 한 건마다 붙는 왕복이다. 2단계에서 TPS를 다시 잴 때 이게 섞여 있으면
 * "낙관적 락이 얼마나 느려졌나"를 잴 수 없다.
 *
 * ## 왜 `id`가 아니라 `orderId`라는 이름인가
 *
 * Kotlin에서 `@Id var id: String`은 `getId()`를 자동으로 만든다.
 * `Persistable<String>`도 `getId()`를 요구하므로 두 선언이 JVM 시그니처 수준에서 충돌한다.
 * 프로퍼티를 `private val orderId`로 두고 `@Column(name = "id")`로 컬럼을 맞춘 뒤
 * `getId()`를 직접 구현해 피한다.
 *
 * ## `isNew`를 어떻게 아는가
 *
 * `persisted` 플래그 하나다. `@Transient`라 DB에 저장되지 않는다.
 * `@PostPersist`(방금 INSERT됨)와 `@PostLoad`(DB에서 읽어옴) 둘 다에서 켜는 것이 중요하다.
 * `@PostLoad`를 빠뜨리면 조회해온 엔티티가 자기를 새 것이라고 말하고,
 * 수정하려 할 때 UPDATE 대신 INSERT가 나가 기본키 충돌로 죽는다.
 */
@Table(schema = "\"order\"", name = "orders")
@Entity
class OrderEntity(
    @Id
    @Column(name = "id")
    private val orderId: String,
    var accountId: String,
    var status: String,
    var totalAmount: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
    var version: Int,
) : Persistable<String> {

    @Transient
    private var persisted: Boolean = false

    override fun getId(): String = orderId

    override fun isNew(): Boolean = !persisted

    @PostPersist
    @PostLoad
    fun markPersisted() {
        persisted = true
    }
}
