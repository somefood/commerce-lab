// ============================================================
// settings.gradle.kts — 이 프로젝트에 어떤 모듈들이 있는지 선언하는 파일.
// 여기 include 되지 않은 디렉토리는 Gradle이 모듈로 인식하지 않는다.
// ============================================================

rootProject.name = "commerce-lab"

// 모든 모듈이 공통으로 사용할 저장소 설정 (모듈별 repositories 선언 불필요)
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// --- 실행 가능한 애플리케이션 (bootstrap) ---
include("apps:api")

// --- 도메인 모듈 (비즈니스 로직, 라이브러리 성격) ---
include("modules:common")
include("modules:product")
// Phase가 진행되면 여기에 추가된다:
 include("modules:member")
 include("modules:order")
// include("modules:payment")
