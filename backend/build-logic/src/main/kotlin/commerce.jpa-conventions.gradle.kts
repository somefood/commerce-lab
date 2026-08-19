plugins {
    id("commerce.spring-conventions")
    kotlin("plugin.jpa")
}

// Kotlin 클래스는 기본이 final이고 기본 생성자가 없다. JPA는 둘 다 요구한다.
//   - noarg: 하이버네이트가 리플렉션으로 엔티티를 만들 때 인자 없는 생성자가 필요하다
//   - allopen: 지연 로딩 프록시는 엔티티를 상속해서 만든다. final이면 프록시를 못 만들고
//              모든 연관관계가 즉시 로딩으로 조용히 바뀐다 (N+1의 흔한 원인)
// kotlin("plugin.jpa")는 noarg만 자동 설정한다. allopen은 직접 지정해야 한다.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
}
