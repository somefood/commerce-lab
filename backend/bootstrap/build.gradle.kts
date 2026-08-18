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
