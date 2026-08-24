# ADR-0006: 도메인 모델과 영속성 모델을 분리한다

## 상태

채택 (2026-08-20)

## 배경

JPA를 쓰면 보통 `@Entity`를 붙인 클래스 하나가 도메인 모델이자 테이블 매핑이 된다.
클래스가 하나면 매핑 코드가 없으니 편하다. 대부분의 스프링 프로젝트가 이렇게 한다.

문제는 **JPA가 클래스에 요구하는 것들**이다.

- 인자 없는 기본 생성자가 있어야 한다 (리플렉션으로 만들기 때문)
- `final`이면 안 된다 (지연 로딩 프록시가 상속으로 만들어지기 때문)
- 필드가 변경 가능해야 한다 (더티 체킹이 값을 바꾸기 때문)

Kotlin의 `data class`는 정반대다. 기본이 `final`이고, `val`은 불변이고,
상태 전이는 `copy()`로 새 객체를 만든다.

한 클래스가 둘을 다 만족하려면 **불변을 포기해야 한다.**
그러면 `Order.markPaid()`가 새 객체를 돌려주는 대신 자기 자신을 바꾸게 되고,
"이 주문이 언제 어떻게 바뀌었는지"를 추적할 수 없게 된다.

## 결정

**두 개로 나눈다.**

```
domain/Order.kt           data class. 불변. JPA를 모른다
infrastructure/OrderEntity.kt   @Entity. var. 하이버네이트 전용
infrastructure/OrderRepositoryAdapter.kt   둘 사이를 번역한다
```

- **포트**(인터페이스)는 `domain`에 둔다 — `OrderRepository`
- **어댑터**(구현)는 `infrastructure`에 둔다 — `OrderRepositoryAdapter`
- `domain`은 `jakarta.persistence`, `org.springframework.data`, `org.hibernate`를 import할 수 없다

말로만 두지 않고 ArchUnit으로 막았다.

```kotlin
noClasses().that().resideInAPackage("com.commercelab.order.domain..")
    .should().dependOnClassesThat().resideInAnyPackage(
        "jakarta.persistence..", "org.springframework.data..", "org.hibernate..")
```

매핑이 귀찮아서 도메인에 `@Entity`를 붙이는 순간 **빌드가 깨진다.**

## 대안

**(가) `@Entity` 하나로 겸용 (가장 흔한 방식)**

버린 이유는 위에 쓴 대로 불변을 포기해야 한다는 것이다. 구체적으로 무엇을 잃는가.

- `Order.place()`가 `DomainResult`나 새 객체를 돌려주는 대신 자기를 바꾼다.
  "실패했을 때 원본이 그대로인가"를 보장할 수 없다
- 3단계에서 쓸 조건부 UPDATE(`WHERE reserved + ? <= total`)를 더티 체킹으로 표현할 수 없다.
  결국 도메인 클래스 옆에 SQL이 붙는다
- 테스트에서 도메인 객체 하나 만들려면 하이버네이트가 필요해진다

**(나) 도메인은 분리하되 포트도 `infrastructure`에 두기**

인터페이스까지 인프라 쪽에 두면 `domain`이 저장에 대해 아무것도 모르게 된다.

버린 이유: 그러면 `application` 서비스가 인프라 패키지를 직접 참조하게 된다.
포트를 `domain`에 두는 것이 의존성 역전의 핵심이다 —
**필요한 쪽이 인터페이스를 소유하고, 구현하는 쪽이 그걸 따른다.**

**(다) 매핑을 MapStruct 같은 도구로 자동화**

지금 규모에서는 도구를 하나 더 배우고 빌드에 애노테이션 프로세서를 붙이는 비용이
손으로 쓰는 매핑 함수 몇 개보다 크다. 필드가 늘어나면 다시 생각한다.

## 결과

**얻은 것**

- 도메인이 불변이다. `Order.markPaid(now)`는 새 `Order`를 돌려주고 원본은 그대로다
- 도메인 테스트에 스프링이 필요 없다. `OrderTest` 17건이 순수 JUnit으로 돈다
- DB 스키마를 바꿔도 도메인이 안 바뀐다. 반대도 마찬가지다
- 3단계에서 조건부 UPDATE를 어댑터 안에 넣을 수 있다. 도메인은 그걸 모른다

**포기한 것**

- **매핑 코드를 손으로 쓴다.** 지금 3쌍(Product/Order/Inventory)이고 필드가 늘면 같이 는다
- 클래스 개수가 두 배다. `Order`를 찾으면 `Order`와 `OrderEntity`가 같이 나온다
- 매핑 함수에 버그가 나면 조용하다. 컬럼 하나를 빠뜨려도 컴파일은 통과한다
- 애그리거트가 번거롭다. `Order`는 `lines`를 갖는데 `OrderEntity`는 안 갖는다.
  `save(order)` 한 번이 테이블 두 개에 써야 하고, FK 때문에 순서까지 지켜야 한다

**언제 이 결정이 과한가.** CRUD가 대부분이고 도메인 규칙이 거의 없는 프로젝트라면
매핑 비용만 내고 얻는 게 없다. 이 프로젝트는 상태 전이와 동시성 제어가 핵심이라
불변성의 값어치가 매핑 비용보다 크다고 봤다.

## 관련

- [M1 §4-1](../milestones/M1-order-core.md)
- 강제 장치: `ArchitectureTest.order 도메인은 영속성 기술을 알지 못한다`
- 어댑터 예시: `order-core/.../infrastructure/OrderRepositoryAdapter.kt`
- 상위 결정: [ADR-0001](./0001-modular-monolith.md)
