plugins {
    id("commerce.spring-conventions")
}

dependencies {
    implementation(project(":modules:payment:payment-api"))
    implementation(project(":modules:common"))
    implementation(project(":modules:contract"))
    implementation("org.springframework:spring-context")
}
