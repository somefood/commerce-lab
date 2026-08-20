import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    `java-library`
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    // Spring Boot BOM을 Gradle 네이티브 platform()으로 가져온다.
    //
    // io.spring.dependency-management 플러그인을 쓰지 않는 이유:
    // 그 플러그인은 Maven식 의존성 관리를 Gradle에 얹은 것으로, legacy Usage 속성
    // ('java-api-jars')을 선언해 Gradle 10에서 에러가 된다. platform()은 Gradle이
    // 원래 가진 기능이라 그런 변환 계층이 없다.
    //
    // implementation에 걸면 compileClasspath와 runtimeClasspath 양쪽에 제약이 전파된다.
    // testImplementation은 별도 계층이라 따로 걸어야 한다.
    "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
    "testImplementation"(platform(SpringBootPlugin.BOM_COORDINATES))

    "testImplementation"(kotlin("test"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }

    // Testcontainers가 쓰는 docker-java는 API 버전 협상에 실패하면 1.32로 떨어진다.
    // Docker 29부터 최소 지원 API가 1.40이라 그 요청이 400으로 거부되고, 겉으로는
    // "Could not find a valid Docker environment"로 보인다. 소켓 문제가 아니라 버전 문제다.
    //
    // 1.41은 Docker 20.10의 API다. 최소선(1.40)보다 높고, 그보다 새로운 서버는
    // 하위 호환으로 받아준다. 특정 머신에서 다른 값이 필요하면 DOCKER_API_VERSION으로 덮는다.
    systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.41")
}
