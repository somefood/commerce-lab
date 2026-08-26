// ============================================================
// 루트 build.gradle.kts
// 여기서는 플러그인을 "선언만" 하고 (apply false), 실제 적용은 각 모듈이 한다.
// → 모든 모듈이 같은 플러그인 버전을 공유하게 만드는 멀티모듈 관례.
// ============================================================

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "com.commercelab"
    version = "0.0.1-SNAPSHOT"
}
