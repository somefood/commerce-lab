plugins {
    // JPA 엔티티를 여기에 둔다. 컨벤션 플러그인이 noarg/allopen과 data-jpa를 함께 붙인다.
    id("commerce.jpa-conventions")
}

dependencies {
    implementation(project(":modules:order:order-api"))
    implementation(project(":modules:common"))
    implementation(project(":modules:contract"))
    implementation("org.springframework:spring-context")
}
