plugins {
    id("commerce.spring-conventions")
}

dependencies {
    implementation(project(":modules:order:order-api"))
    implementation(project(":modules:common"))
    implementation(project(":modules:contract"))
    implementation("org.springframework:spring-context")
}
