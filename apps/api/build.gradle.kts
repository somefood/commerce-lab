// ============================================================
// apps:api — 유일한 "실행 가능한" 모듈 (bootstrap 모듈)
// 역할: 도메인 모듈들을 조립해서 하나의 Spring Boot 앱으로 띄운다.
// 비즈니스 로직은 여기 두지 않는다. 조립과 실행만 담당.
// ============================================================

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot) // 이 모듈만 bootJar(실행 가능한 jar)를 만든다
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // JSR-305: 자바 라이브러리의 @Nullable/@NonNull을 Kotlin 타입 시스템에 엄격 반영
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    // Spring Boot BOM — 스프링 관련 의존성 버전을 통일
    implementation(platform(libs.spring.boot.bom))

    // 도메인 모듈 조립
    implementation(project(":modules:common"))
    implementation(project(":modules:product"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa") // 엔티티 스캔/트랜잭션 설정 주체
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin") // Kotlin data class <-> JSON 변환

    // 로컬 개발용 인메모리 DB (Phase 3에서 PostgreSQL로 교체 예정)
    runtimeOnly("com.h2database:h2")
    implementation("org.springframework.boot:spring-boot-h2console")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
