plugins {
    id("commerce.spring-boot-app-conventions")
}

dependencies {
    implementation(project(":modules:order:order-core"))
    implementation(project(":modules:payment:payment-core"))
    implementation(project(":modules:order:order-api"))
    implementation(project(":modules:payment:payment-api"))
    implementation(project(":modules:common"))
    implementation(project(":modules:contract"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.springdoc.openapi.webmvc)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // 스키마 마이그레이션. 실행되는 DDL이 레포에 SQL 파일로 남는다.
    // ddl-auto를 쓰지 않는 이유는 docs/milestones/M1-order-core.md §2에 있다.
    implementation("org.flywaydb:flyway-core")
    // Flyway 10부터 DB별 지원이 별도 아티팩트로 분리됐다. 이게 없으면 런타임에
    // "Unsupported Database: PostgreSQL"로 죽는다.
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    implementation("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.archunit.junit5)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.register<Test>("archTest") {
    description = "아키텍처 불변 규칙만 검사한다"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.commercelab.architecture.*")
    }
}
