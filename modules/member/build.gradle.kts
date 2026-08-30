// ============================================================
// modules:member — 상품 도메인 모듈 (헥사고날 아키텍처)
//
// 패키지 구조 (안쪽 → 바깥쪽):
//   domain/                     핵심 비즈니스 모델. 스프링/JPA를 모른다.
//   application/port/in/        유스케이스 인터페이스 (외부에서 도메인을 호출하는 문)
//   application/port/out/       도메인이 외부(DB 등)에 요구하는 인터페이스
//   application/service/        유스케이스 구현체
//   adapter/in/web/             REST 컨트롤러 (들어오는 어댑터)
//   adapter/out/persistence/    JPA 엔티티/리포지토리 (나가는 어댑터)
//
// 의존 방향은 항상 바깥→안쪽: adapter → application → domain
// domain은 아무것도 의존하지 않는다.
// ============================================================

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

// Kotlin 클래스는 기본 final인데 JPA는 지연 로딩 프록시를 위해 open이 필요.
// 아래 애노테이션이 붙은 클래스를 컴파일 시점에 open으로 바꿔준다.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))

    implementation(project(":modules:common"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")      // adapter/in/web 용
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")    // adapter/out/persistence 용
    implementation("org.springframework.boot:spring-boot-starter-validation")  // 요청 DTO 검증용
    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
