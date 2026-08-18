plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    // kotlin("plugin.spring")과 kotlin("plugin.jpa")는 kotlin-gradle-plugin이 아니라
    // allopen/noarg 아티팩트에 들어 있다. 빼먹으면 "Plugin not found"로 빌드가 죽는다.
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.kotlin.noarg.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.plugin)
}
