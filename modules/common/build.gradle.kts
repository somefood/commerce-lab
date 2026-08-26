// ============================================================
// modules:common — 공유 커널 (Shared Kernel)
// 모든 도메인 모듈이 공통으로 쓰는 최소한의 코드만 둔다.
// 예: 공통 예외 타입, 도메인 이벤트 인터페이스, ID 생성기.
//
// ⚠️ 팀장 주의사항: common 모듈은 "쓰레기장"이 되기 쉽다.
// "여기저기서 쓰이니까 common에 넣자"를 반복하면 모든 모듈이
// common에 강결합된다. 정말 공통인 것만 엄격하게 허용할 것.
// ============================================================

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(platform(libs.spring.boot.bom))

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
