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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.archunit.junit5)
}
