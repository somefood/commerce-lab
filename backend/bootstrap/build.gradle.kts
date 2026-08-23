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
    // Kotlin은 생성자 파라미터 이름을 바이트코드에 남기지 않는다(-java-parameters 없이는
    // MethodParameters 속성이 안 붙는다). 그래서 Boot가 기본 등록하는
    // jackson-module-parameter-names만으로는 data class를 역직렬화할 수 없고,
    // @RequestBody가 400 "no Creators, like default constructor, exist"로 끝난다.
    // 이 모듈이 Kotlin 메타데이터를 읽어 주 생성자를 Creator로 인식시키고,
    // 파라미터 기본값과 non-null 검사도 함께 살려준다.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.springdoc.openapi.webmvc)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // 앱 클래스가 @EntityScan / @EnableJpaRepositories로 모듈의 영속성 계층을 조립한다.
    // order-core는 JPA를 implementation으로 갖고 있어 밖으로 새지 않으므로 여기에도 필요하다.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

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
