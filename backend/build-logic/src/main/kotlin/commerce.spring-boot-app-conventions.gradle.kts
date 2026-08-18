plugins {
    id("commerce.spring-conventions")
    id("org.springframework.boot")
}

// 이 플러그인이 따로 존재하는 이유:
// org.springframework.boot 플러그인은 build-logic의 클래스패스에는 있지만,
// 메인 빌드의 build.gradle.kts에서 id("org.springframework.boot")로 부르면
// 버전을 못 찾아 "Plugin not found"가 난다. build-logic 안에서 한 번 감싸면
// 버전 없이 적용할 수 있고, 실행 가능한 앱 모듈이 무엇인지도 이름으로 드러난다.
