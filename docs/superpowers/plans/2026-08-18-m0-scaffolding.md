# M0 스캐폴딩 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 M1부터 도메인 구현에만 집중할 수 있도록, 경계가 강제되고 실행 가능한 개발 환경을 완성한다.

**Architecture:** Gradle 멀티모듈(`build-logic` 컨벤션 플러그인 + 버전 카탈로그) 위에 `common` / `contract` / `order` / `payment` / `bootstrap`을 배치한다. 모듈 간 core 참조는 Gradle 의존성 그래프로 차단하고, 나머지 불변 규칙은 ArchUnit 테스트로 강제한다. 인프라는 Docker Compose로 한 번에 기동하고, 백엔드 통합 테스트는 Testcontainers로 실제 Postgres에 붙는다.

**Tech Stack:** Kotlin 2.1.20 / Spring Boot 3.5.3 / Gradle 8.14.3 / Java 21 / PostgreSQL 16 / Redis 7 / Redpanda / ArchUnit 1.3.0 / Testcontainers / springdoc-openapi 2.8.x / Next.js 15 + TypeScript + TanStack Query v5 + Tailwind v4 / GitHub Actions

**Spec:** `docs/superpowers/specs/2026-08-18-commerce-lab-design.md`

## Global Constraints

- 이 계획의 **모든 작업은 Claude가 수행한다.** M0는 설계문서 §8에서 Claude 전담으로 명시되어 있다.
- 루트 패키지는 `com.commercelab`. 모듈별 하위 패키지는 `common` / `contract` / `order` / `payment` / `bootstrap`.
- Java 툴체인 21 고정. `JAVA_HOME`은 `/Users/seokjuhong/.sdkman/candidates/java/current`.
- Gradle CLI가 설치돼 있지 않다. 모든 Gradle 실행은 `./gradlew` 래퍼로 한다.
- 패키지 매니저는 **npm**을 쓴다 (pnpm 미설치). 설계문서와 CLAUDE.md의 `pnpm` 표기는 Task 6에서 수정한다.
- k6는 설치하지 않는다. **Docker 이미지 `grafana/k6`로 실행**한다.
- Docker 데몬(OrbStack)이 꺼져 있을 수 있다. 인프라가 필요한 작업 전에 기동 여부를 먼저 확인한다.
- 포트 배정: 백엔드 8080 / 프론트 3000 / Postgres 5432 / Redis 6379 / Redpanda 9092 / Prometheus 9090 / **Grafana 3001** (프론트와 충돌 회피).
- DB 스키마는 `order`와 `payment` 두 개로 분리한다. `public` 스키마에 도메인 테이블을 만들지 않는다.
- jOOQ는 M0 범위가 아니다. 조회 최적화가 실제로 필요해지는 M1 3단계에서 도입한다.
- 커밋 메시지는 Conventional Commits(`feat:`, `chore:`, `test:`, `docs:`, `ci:`)를 따른다.

---

## File Structure

작업 전 전체 배치를 확정한다. 각 파일의 책임은 하나다.

```
backend/
├─ gradlew, gradlew.bat, gradle/wrapper/      Gradle 8.14.3 래퍼 (kopring-shop에서 복사)
├─ gradle/libs.versions.toml                  버전 카탈로그 — 버전은 여기 한 곳에만 존재
├─ settings.gradle.kts                        모듈 포함 목록, build-logic 합성 빌드
├─ build.gradle.kts                           루트. 실제 설정 없음(모든 설정은 컨벤션 플러그인)
├─ build-logic/
│  ├─ settings.gradle.kts                     버전 카탈로그 재사용 선언
│  ├─ build.gradle.kts                        kotlin-dsl, 플러그인 의존성
│  └─ src/main/kotlin/
│     ├─ commerce.kotlin-conventions.gradle.kts    Kotlin/Java 툴체인, 테스트, BOM
│     └─ commerce.spring-conventions.gradle.kts    위 + kotlin-spring 플러그인
├─ modules/
│  ├─ common/          Money, DomainError 등 공용 값 타입. 프레임워크 의존 없음
│  ├─ contract/        모듈 간 이벤트 계약. 의존성 0 (ArchUnit이 감시)
│  ├─ order/
│  │  ├─ order-api/    다른 모듈이 볼 수 있는 유일한 창구 (인터페이스)
│  │  └─ order-core/   주문 도메인 구현. 외부 모듈에서 컴파일 불가
│  └─ payment/
│     ├─ payment-api/
│     └─ payment-core/
└─ bootstrap/
   ├─ src/main/kotlin/com/commercelab/bootstrap/
   │  ├─ CommerceLabApplication.kt            Boot 진입점
   │  └─ web/HealthController.kt              /api/health — 프론트 연동 확인용
   ├─ src/main/resources/application.yml      로컬 프로필 설정
   └─ src/test/kotlin/com/commercelab/
      ├─ architecture/ArchitectureTest.kt     불변 규칙 강제
      └─ bootstrap/HealthIntegrationTest.kt   Testcontainers 통합 테스트

infra/
├─ docker-compose.yml                          Postgres/Redis/Redpanda/Prometheus/Grafana
├─ postgres/init/01-schemas.sql                order, payment 스키마 생성
├─ prometheus/prometheus.yml                   백엔드 actuator 스크레이프 설정
└─ k6/smoke.js                                 M1 부하 시나리오의 뼈대

frontend/
├─ src/app/page.tsx                            백엔드 헬스 상태 표시
├─ src/app/providers.tsx                       TanStack Query Provider
├─ src/lib/api.ts                              생성된 타입을 쓰는 fetch 래퍼
├─ src/types/api.d.ts                          OpenAPI에서 생성 (커밋함)
└─ package.json                                gen:api 스크립트 포함

.github/workflows/ci.yml                        백엔드 테스트 + 프론트 빌드
docs/PROGRESS.md                                마일스톤 진도 추적
```

**책임 분리 근거:** 버전은 `libs.versions.toml` 한 곳, 빌드 규칙은 `build-logic` 한 곳에만 둔다. 모듈 `build.gradle.kts`는 의존성 선언만 남아 3~7줄이 된다. 이렇게 하면 M1 이후 사용자가 모듈 빌드 파일을 열었을 때 읽을 것이 의존 관계뿐이라, 모듈 경계가 눈에 바로 들어온다.

---

## Task 1: Gradle 멀티모듈 뼈대

**Files:**
- Copy: `/Users/seokjuhong/workspace/kopring-shop/backend/gradlew` → `backend/gradlew`
- Copy: `/Users/seokjuhong/workspace/kopring-shop/backend/gradlew.bat` → `backend/gradlew.bat`
- Copy: `/Users/seokjuhong/workspace/kopring-shop/backend/gradle/wrapper/` → `backend/gradle/wrapper/`
- Create: `backend/gradle/libs.versions.toml`
- Create: `backend/settings.gradle.kts`
- Create: `backend/build.gradle.kts`
- Create: `backend/build-logic/settings.gradle.kts`
- Create: `backend/build-logic/build.gradle.kts`
- Create: `backend/build-logic/src/main/kotlin/commerce.kotlin-conventions.gradle.kts`
- Create: `backend/build-logic/src/main/kotlin/commerce.spring-conventions.gradle.kts`
- Create: `backend/modules/common/build.gradle.kts`
- Create: `backend/modules/contract/build.gradle.kts`
- Create: `backend/modules/order/order-api/build.gradle.kts`
- Create: `backend/modules/order/order-core/build.gradle.kts`
- Create: `backend/modules/payment/payment-api/build.gradle.kts`
- Create: `backend/modules/payment/payment-core/build.gradle.kts`
- Create: `backend/bootstrap/build.gradle.kts`
- Test: `backend/modules/common/src/test/kotlin/com/commercelab/common/MoneyTest.kt`
- Create: `backend/modules/common/src/main/kotlin/com/commercelab/common/Money.kt`

**Interfaces:**
- Consumes: 없음 (첫 작업)
- Produces:
  - Gradle 프로젝트 경로: `:modules:common`, `:modules:contract`, `:modules:order:order-api`, `:modules:order:order-core`, `:modules:payment:payment-api`, `:modules:payment:payment-core`, `:bootstrap`
  - 컨벤션 플러그인 ID: `commerce.kotlin-conventions`, `commerce.spring-conventions`
  - `com.commercelab.common.Money` — `@JvmInline value class Money(val amount: Long)`, 연산 `plus(other: Money): Money`, `times(qty: Int): Money`, 팩토리 `Money.of(amount: Long): Money` (음수면 `IllegalArgumentException`)

- [ ] **Step 1: Gradle 래퍼 복사**

```bash
cd /Users/seokjuhong/workspace/commerce-lab
mkdir -p backend/gradle/wrapper
cp /Users/seokjuhong/workspace/kopring-shop/backend/gradlew backend/gradlew
cp /Users/seokjuhong/workspace/kopring-shop/backend/gradlew.bat backend/gradlew.bat
cp /Users/seokjuhong/workspace/kopring-shop/backend/gradle/wrapper/gradle-wrapper.jar backend/gradle/wrapper/
cp /Users/seokjuhong/workspace/kopring-shop/backend/gradle/wrapper/gradle-wrapper.properties backend/gradle/wrapper/
chmod +x backend/gradlew
```

확인: `cat backend/gradle/wrapper/gradle-wrapper.properties`에 `gradle-8.14.3-bin.zip`이 보여야 한다.

- [ ] **Step 2: 버전 카탈로그 작성**

`backend/gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.1.20"
springBoot = "3.5.3"
springDependencyManagement = "1.1.7"
archunit = "1.3.0"
springdoc = "2.8.6"

[libraries]
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
spring-boot-gradle-plugin = { module = "org.springframework.boot:spring-boot-gradle-plugin", version.ref = "springBoot" }
spring-dependency-management-plugin = { module = "io.spring.gradle:dependency-management-plugin", version.ref = "springDependencyManagement" }
kotlin-allopen-plugin = { module = "org.jetbrains.kotlin:kotlin-allopen", version.ref = "kotlin" }
kotlin-noarg-plugin = { module = "org.jetbrains.kotlin:kotlin-noarg", version.ref = "kotlin" }
archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }
springdoc-openapi-webmvc = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc" }
```

- [ ] **Step 3: build-logic 합성 빌드 작성**

`backend/build-logic/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
```

`backend/build-logic/build.gradle.kts`:

```kotlin
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
```

- [ ] **Step 4: 컨벤션 플러그인 작성**

`backend/build-logic/src/main/kotlin/commerce.kotlin-conventions.gradle.kts`:

```kotlin
plugins {
    `java-library`
    kotlin("jvm")
    id("io.spring.dependency-management")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    "testImplementation"(kotlin("test"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
```

`backend/build-logic/src/main/kotlin/commerce.spring-conventions.gradle.kts`:

```kotlin
plugins {
    id("commerce.kotlin-conventions")
    kotlin("plugin.spring")
}
```

`backend/build-logic/src/main/kotlin/commerce.spring-boot-app-conventions.gradle.kts`:

```kotlin
plugins {
    id("commerce.spring-conventions")
    id("org.springframework.boot")
}

// 이 플러그인이 따로 존재하는 이유:
// org.springframework.boot 플러그인은 build-logic의 클래스패스에는 있지만,
// 메인 빌드의 build.gradle.kts에서 id("org.springframework.boot")로 부르면
// 버전을 못 찾아 "Plugin not found"가 난다. build-logic 안에서 한 번 감싸면
// 버전 없이 적용할 수 있고, 실행 가능한 앱 모듈이 무엇인지도 이름으로 드러난다.
```

- [ ] **Step 5: settings와 루트 빌드 파일 작성**

`backend/settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "commerce-lab"

include(
    "modules:common",
    "modules:contract",
    "modules:order:order-api",
    "modules:order:order-core",
    "modules:payment:payment-api",
    "modules:payment:payment-core",
    "bootstrap",
)
```

`backend/build.gradle.kts`:

```kotlin
// 의도적으로 비어 있다.
// 모든 공통 설정은 build-logic의 컨벤션 플러그인에 있다.
// allprojects/subprojects 블록을 쓰지 않는 이유: 모듈이 자기 설정을 명시적으로
// 선택하게 해야 "이 모듈이 무엇인지"가 build.gradle.kts만 보고 드러난다.
```

- [ ] **Step 6: 모듈 빌드 파일 7개 작성**

`backend/modules/common/build.gradle.kts`:

```kotlin
plugins {
    id("commerce.kotlin-conventions")
}
```

`backend/modules/contract/build.gradle.kts`:

```kotlin
plugins {
    id("commerce.kotlin-conventions")
}

// 의존성 없음. 이벤트 계약은 어떤 프레임워크도 알아서는 안 된다.
// ArchUnit ContractPurity 규칙이 이를 감시한다.
```

`backend/modules/order/order-api/build.gradle.kts`:

```kotlin
plugins {
    id("commerce.kotlin-conventions")
}

dependencies {
    api(project(":modules:common"))
}
```

`backend/modules/order/order-core/build.gradle.kts`:

```kotlin
plugins {
    id("commerce.spring-conventions")
}

dependencies {
    implementation(project(":modules:order:order-api"))
    implementation(project(":modules:common"))
    implementation(project(":modules:contract"))
    implementation("org.springframework:spring-context")
}
```

`backend/modules/payment/payment-api/build.gradle.kts`:

```kotlin
plugins {
    id("commerce.kotlin-conventions")
}

dependencies {
    api(project(":modules:common"))
}
```

`backend/modules/payment/payment-core/build.gradle.kts`:

```kotlin
plugins {
    id("commerce.spring-conventions")
}

dependencies {
    implementation(project(":modules:payment:payment-api"))
    implementation(project(":modules:common"))
    implementation(project(":modules:contract"))
    implementation("org.springframework:spring-context")
}
```

`backend/bootstrap/build.gradle.kts`:

```kotlin
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
```

주의: `bootstrap`은 `order-core`와 `payment-core`를 **둘 다** 의존하는 유일한 모듈이다. 조립은 여기서만 일어난다.

- [ ] **Step 7: 실패하는 테스트 작성**

`backend/modules/common/src/test/kotlin/com/commercelab/common/MoneyTest.kt`:

```kotlin
package com.commercelab.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `같은 통화 금액을 더한다`() {
        val result = Money.of(1_000) + Money.of(2_500)
        assertEquals(Money.of(3_500), result)
    }

    @Test
    fun `수량만큼 곱한다`() {
        val result = Money.of(1_200) * 3
        assertEquals(Money.of(3_600), result)
    }

    @Test
    fun `음수 금액은 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            Money.of(-1)
        }
    }
}
```

- [ ] **Step 8: 테스트 실패 확인**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew :modules:common:test
```

기대: 컴파일 실패. `Unresolved reference: Money`

(첫 실행은 Gradle 8.14.3 배포판을 내려받으므로 수 분 걸릴 수 있다.)

- [ ] **Step 9: 최소 구현 작성**

`backend/modules/common/src/main/kotlin/com/commercelab/common/Money.kt`:

```kotlin
package com.commercelab.common

/**
 * 금액. 원 단위 정수로만 다룬다.
 *
 * Double을 쓰지 않는 이유: 부동소수점 오차가 원장 합계 검증(차변합 = 대변합)을
 * 깨뜨린다. 금액은 언제나 정수로 저장하고 표시할 때만 포맷한다.
 */
@JvmInline
value class Money private constructor(val amount: Long) {

    operator fun plus(other: Money): Money = Money(amount + other.amount)

    operator fun times(quantity: Int): Money = Money(amount * quantity)

    companion object {
        val ZERO: Money = Money(0)

        fun of(amount: Long): Money {
            require(amount >= 0) { "금액은 음수일 수 없습니다: $amount" }
            return Money(amount)
        }
    }
}
```

- [ ] **Step 10: 테스트 통과 확인**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew :modules:common:test
```

기대: `BUILD SUCCESSFUL`, 3개 테스트 통과

- [ ] **Step 11: 전체 모듈 컴파일 확인**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew build -x test
```

기대: `BUILD SUCCESSFUL`. 7개 모듈이 모두 인식되어야 한다.

`./gradlew projects`로 모듈 트리를 눈으로 확인한다.

- [ ] **Step 12: 커밋**

```bash
cd /Users/seokjuhong/workspace/commerce-lab
cat >> .gitignore <<'EOF'

# gradle wrapper jar는 커밋한다 (다른 머신에서 clone 후 바로 빌드 가능해야 함)
!gradle/wrapper/gradle-wrapper.jar
EOF
git add -A
git commit -m "feat: Gradle 멀티모듈 뼈대와 Money 값 타입 추가

build-logic 컨벤션 플러그인과 버전 카탈로그로 빌드 설정을 한 곳에 모은다.
모듈 간 core 참조는 Gradle 의존성 그래프에서 원천 차단된다."
```

---

## Task 2: ArchUnit 아키텍처 규칙

**Files:**
- Create: `backend/bootstrap/src/test/kotlin/com/commercelab/architecture/ArchitectureTest.kt`
- Modify: `backend/bootstrap/build.gradle.kts` (archTest 태스크 추가)
- Create: `backend/modules/contract/src/main/kotlin/com/commercelab/contract/OrderPlaced.kt`
- Create: `backend/modules/order/order-core/src/main/kotlin/com/commercelab/order/OrderPlacer.kt` (규칙 검증용 최소 클래스)

**Interfaces:**
- Consumes: Task 1의 `commerce.kotlin-conventions`, `:bootstrap` 프로젝트, `com.commercelab.common.Money`
- Produces:
  - `com.commercelab.contract.OrderPlaced` — `data class OrderPlaced(eventId: String, orderId: String, accountId: String, amount: Long, occurredAt: java.time.Instant)`
  - Gradle 태스크 `archTest` — ArchUnit 테스트만 실행
  - M1 이후 모든 코드가 지켜야 할 3개 규칙

- [ ] **Step 1: 규칙 검증에 쓸 최소 클래스 작성**

먼저 규칙이 검사할 대상이 있어야 한다.

`backend/modules/contract/src/main/kotlin/com/commercelab/contract/OrderPlaced.kt`:

```kotlin
package com.commercelab.contract

import java.time.Instant

/**
 * 주문이 생성되었음을 알리는 이벤트.
 *
 * 원시 타입만 쓰는 이유: M4에서 payment가 별도 프로세스로 분리되면 이 계약은
 * JSON으로 직렬화된다. Money 같은 도메인 타입이 계약에 새면 양쪽 서비스가
 * 같은 클래스를 공유해야 하고, 그 순간 독립 배포가 불가능해진다.
 */
data class OrderPlaced(
    val eventId: String,
    val orderId: String,
    val accountId: String,
    val amount: Long,
    val occurredAt: Instant,
)
```

`backend/modules/order/order-core/src/main/kotlin/com/commercelab/order/OrderPlacer.kt`:

```kotlin
package com.commercelab.order

import com.commercelab.common.Money
import com.commercelab.contract.OrderPlaced
import java.time.Instant

/**
 * M0 시점의 자리표. M1에서 실제 주문 생성 로직으로 교체된다.
 * 지금은 ArchUnit 규칙이 검사할 대상이 존재하게 하는 것이 목적이다.
 */
class OrderPlacer {

    fun place(orderId: String, accountId: String, amount: Money): OrderPlaced =
        OrderPlaced(
            eventId = orderId,
            orderId = orderId,
            accountId = accountId,
            amount = amount.amount,
            occurredAt = Instant.EPOCH,
        )
}
```

- [ ] **Step 2: 실패하는 아키텍처 테스트 작성**

`backend/bootstrap/src/test/kotlin/com/commercelab/architecture/ArchitectureTest.kt`:

```kotlin
package com.commercelab.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * 설계문서 §3.3의 불변 규칙을 강제한다.
 * 이 테스트가 깨지면 구현이 아니라 설계가 무너진 것이다. 규칙을 고쳐서 통과시키지 말 것.
 */
class ArchitectureTest {

    companion object {
        private lateinit var classes: JavaClasses

        @BeforeAll
        @JvmStatic
        fun importClasses() {
            classes = ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.commercelab")
        }
    }

    @Test
    fun `order는 payment를 알지 못한다`() {
        noClasses()
            .that().resideInAPackage("com.commercelab.order..")
            .should().dependOnClassesThat().resideInAPackage("com.commercelab.payment..")
            .because("모듈 간 통신은 이벤트로만 한다. 직접 참조는 M4 분리를 불가능하게 만든다")
            .check(classes)
    }

    @Test
    fun `payment는 order를 알지 못한다`() {
        noClasses()
            .that().resideInAPackage("com.commercelab.payment..")
            .should().dependOnClassesThat().resideInAPackage("com.commercelab.order..")
            .because("모듈 간 통신은 이벤트로만 한다. 직접 참조는 M4 분리를 불가능하게 만든다")
            .check(classes)
    }

    @Test
    fun `contract는 어떤 프레임워크도 알지 못한다`() {
        noClasses()
            .that().resideInAPackage("com.commercelab.contract..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta..",
                "com.fasterxml.jackson..",
            )
            .because("계약은 JSON으로 직렬화되어 프로세스 경계를 넘는다. 프레임워크에 묶이면 독립 배포가 죽는다")
            .check(classes)
    }

    @Test
    fun `bootstrap 클래스에는 트랜잭션 경계가 없다`() {
        noClasses()
            .that().resideInAPackage("com.commercelab.bootstrap..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("bootstrap이 트랜잭션을 열면 여러 모듈이 한 트랜잭션에 묶인다. 그러면 M4에서 분리할 수 없다")
            .check(classes)
    }

    @Test
    fun `bootstrap 메서드에도 트랜잭션 경계가 없다`() {
        // 클래스 검사만으로는 부족하다. @Transactional은 메서드에 붙는 경우가 더 흔하다.
        noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("com.commercelab.bootstrap..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("bootstrap이 트랜잭션을 열면 여러 모듈이 한 트랜잭션에 묶인다. 그러면 M4에서 분리할 수 없다")
            .check(classes)
    }
}
```

- [ ] **Step 3: archTest 태스크 추가**

`backend/bootstrap/build.gradle.kts` 맨 아래에 추가:

```kotlin
tasks.register<Test>("archTest") {
    description = "아키텍처 불변 규칙만 검사한다"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.commercelab.architecture.*")
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew :bootstrap:archTest
```

기대: `BUILD SUCCESSFUL`, 5개 테스트 통과

- [ ] **Step 5: 규칙이 실제로 작동하는지 역검증**

규칙이 통과하는 것만으로는 규칙이 살아 있다는 증거가 안 된다. 일부러 어겨본다.

`backend/modules/contract/src/main/kotlin/com/commercelab/contract/OrderPlaced.kt` 상단에 임시로 추가:

```kotlin
import org.springframework.stereotype.Component
```

그리고 파일 맨 아래에 임시 클래스 추가:

```kotlin
@Component
class TemporaryViolation
```

`backend/modules/contract/build.gradle.kts`에 임시로 추가:

```kotlin
dependencies {
    implementation("org.springframework:spring-context")
}
```

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew :bootstrap:archTest
```

기대: **FAIL**. `contract는 어떤 프레임워크도 알지 못한다` 테스트가 깨지고, 메시지에 `TemporaryViolation`이 나온다.

- [ ] **Step 6: 위반 코드 원복**

Step 5에서 추가한 import, `TemporaryViolation` 클래스, `contract/build.gradle.kts`의 dependencies 블록을 모두 제거한다.

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew :bootstrap:archTest
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 7: 커밋**

```bash
cd /Users/seokjuhong/workspace/commerce-lab
git add -A
git commit -m "test: ArchUnit 아키텍처 불변 규칙 5종 추가

모듈 간 상호 참조 금지, contract 순수성, bootstrap 트랜잭션 금지를 강제한다.
규칙이 실제로 위반을 잡는지 역검증까지 마쳤다."
```

---

## Task 3: Docker Compose 인프라

**Files:**
- Create: `infra/docker-compose.yml`
- Create: `infra/postgres/init/01-schemas.sql`
- Create: `infra/prometheus/prometheus.yml`
- Create: `infra/k6/smoke.js`
- Create: `infra/README.md`

**Interfaces:**
- Consumes: 없음 (백엔드 코드와 독립)
- Produces:
  - Postgres `localhost:5432`, DB `commerce`, 사용자 `commerce` / 비밀번호 `commerce`, 스키마 `order`·`payment`
  - Redis `localhost:6379`
  - Redpanda(Kafka API) `localhost:9092`
  - Prometheus `localhost:9090`, Grafana `localhost:3001` (admin/admin)
  - Task 4의 통합 테스트는 이 compose가 아니라 Testcontainers를 쓴다. 둘은 독립이다.

- [ ] **Step 1: Docker 데몬 기동 확인**

```bash
docker info --format '{{.ServerVersion}}'
```

실패하면 (`failed to connect to the docker API`) OrbStack을 먼저 켠다:

```bash
open -a OrbStack
```

30초 정도 기다린 뒤 `docker info` 재실행. 그래도 실패하면 사용자에게 알리고 멈춘다.

- [ ] **Step 2: 스키마 초기화 스크립트 작성**

`infra/postgres/init/01-schemas.sql`:

```sql
-- 모듈별 스키마 분리.
-- 같은 DB를 쓰되 스키마를 나누는 이유: 교차 JOIN을 물리적으로 어렵게 만들어
-- M4의 DB 분리 시점에 코드 변경이 최소가 되도록 한다.

CREATE SCHEMA IF NOT EXISTS "order";
CREATE SCHEMA IF NOT EXISTS payment;

-- 각 모듈은 자기 스키마에만 접근한다.
-- (M1 이후 모듈별 DB 사용자를 분리하면 이 규칙을 DB 권한으로도 강제할 수 있다.)
GRANT ALL ON SCHEMA "order" TO commerce;
GRANT ALL ON SCHEMA payment TO commerce;
```

주의: `order`는 SQL 예약어라 반드시 큰따옴표로 감싼다.

- [ ] **Step 3: Prometheus 설정 작성**

`infra/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 5s

scrape_configs:
  - job_name: commerce-lab-backend
    metrics_path: /actuator/prometheus
    static_configs:
      # 컨테이너에서 호스트의 백엔드(8080)에 붙는다
      - targets: ["host.docker.internal:8080"]
```

- [ ] **Step 4: compose 파일 작성**

`infra/docker-compose.yml`:

```yaml
name: commerce-lab

services:
  postgres:
    image: postgres:16-alpine
    container_name: commerce-postgres
    environment:
      POSTGRES_DB: commerce
      POSTGRES_USER: commerce
      POSTGRES_PASSWORD: commerce
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./postgres/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U commerce -d commerce"]
      interval: 5s
      timeout: 3s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: commerce-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10

  redpanda:
    image: redpandadata/redpanda:v24.2.7
    container_name: commerce-redpanda
    command:
      - redpanda
      - start
      - --smp=1
      - --overprovisioned
      - --node-id=0
      - --check=false
      - --kafka-addr=PLAINTEXT://0.0.0.0:9092
      - --advertise-kafka-addr=PLAINTEXT://localhost:9092
    ports:
      - "9092:9092"
      - "9644:9644"
    healthcheck:
      test: ["CMD-SHELL", "rpk cluster health | grep -q 'Healthy:.*true'"]
      interval: 10s
      timeout: 5s
      retries: 10

  prometheus:
    image: prom/prometheus:v3.1.0
    container_name: commerce-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    extra_hosts:
      - "host.docker.internal:host-gateway"

  grafana:
    image: grafana/grafana:11.5.1
    container_name: commerce-grafana
    # 프론트엔드가 3000을 쓰므로 3001로 옮긴다
    ports:
      - "3001:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin
      GF_AUTH_ANONYMOUS_ENABLED: "true"
    depends_on:
      - prometheus

volumes:
  postgres-data:
```

- [ ] **Step 5: 인프라 기동 및 검증**

```bash
cd /Users/seokjuhong/workspace/commerce-lab
docker compose -f infra/docker-compose.yml up -d
sleep 20
docker compose -f infra/docker-compose.yml ps
```

기대: postgres, redis, redpanda가 `healthy`. prometheus, grafana가 `running`.

- [ ] **Step 6: 스키마 생성 검증**

```bash
docker exec commerce-postgres psql -U commerce -d commerce -c "\dn"
```

기대 출력에 `order`와 `payment` 스키마가 포함되어야 한다.

- [ ] **Step 7: Redpanda 토픽 생성 확인**

```bash
docker exec commerce-redpanda rpk topic create smoke-test
docker exec commerce-redpanda rpk topic list
docker exec commerce-redpanda rpk topic delete smoke-test
```

기대: 생성 → 목록에 표시 → 삭제 성공

- [ ] **Step 8: k6 스모크 시나리오 작성**

`infra/k6/smoke.js`:

```javascript
// k6는 로컬에 설치하지 않는다. Docker로 실행한다:
//   docker run --rm -i --network host grafana/k6 run - < infra/k6/smoke.js
//
// M1에서 이 파일을 확장해 동시 주문 부하를 걸고 오버셀을 재현한다.

import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 10,
  duration: '10s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const res = http.get('http://localhost:8080/api/health');
  check(res, {
    'status is 200': (r) => r.status === 200,
    'status field is UP': (r) => r.json('status') === 'UP',
  });
}
```

- [ ] **Step 9: 인프라 문서 작성**

`infra/README.md`:

```markdown
# 로컬 인프라

## 기동

    docker compose -f infra/docker-compose.yml up -d

## 접속 정보

| 서비스 | 주소 | 계정 |
|---|---|---|
| PostgreSQL | localhost:5432 | commerce / commerce (DB: commerce) |
| Redis | localhost:6379 | - |
| Redpanda (Kafka) | localhost:9092 | - |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3001 | admin / admin |

## 자주 쓰는 명령

    # 스키마 확인
    docker exec commerce-postgres psql -U commerce -d commerce -c "\dn"

    # 토픽 목록
    docker exec commerce-redpanda rpk topic list

    # 부하 테스트 (k6 설치 불필요)
    docker run --rm -i --network host grafana/k6 run - < infra/k6/smoke.js

    # 전체 정리 (데이터까지 삭제)
    docker compose -f infra/docker-compose.yml down -v

## 참고

Grafana는 3001 포트를 쓴다. 프론트엔드가 3000을 점유하기 때문이다.
```

- [ ] **Step 10: 커밋**

```bash
cd /Users/seokjuhong/workspace/commerce-lab
git add -A
git commit -m "feat: 로컬 인프라 Docker Compose 구성

Postgres(스키마 분리) / Redis / Redpanda / Prometheus / Grafana.
k6는 설치 없이 Docker 이미지로 실행한다."
```

---

## Task 4: Boot 앱과 Testcontainers 통합 테스트

**Files:**
- Create: `backend/bootstrap/src/main/kotlin/com/commercelab/bootstrap/CommerceLabApplication.kt`
- Create: `backend/bootstrap/src/main/kotlin/com/commercelab/bootstrap/web/HealthController.kt`
- Create: `backend/bootstrap/src/main/resources/application.yml`
- Modify: `backend/bootstrap/build.gradle.kts` (JDBC, Testcontainers 의존성 추가)
- Test: `backend/bootstrap/src/test/kotlin/com/commercelab/bootstrap/HealthIntegrationTest.kt`

**Interfaces:**
- Consumes: Task 1의 `:bootstrap` 프로젝트와 컨벤션 플러그인, Task 3의 Postgres 스키마 구조
- Produces:
  - `GET /api/health` → `200 OK`, 본문 `{"status":"UP","service":"commerce-lab"}`
  - `GET /v3/api-docs` → OpenAPI 3 JSON (Task 5의 타입 생성 입력)
  - `GET /actuator/prometheus` → Prometheus 메트릭
  - 앱 진입점 `com.commercelab.bootstrap.CommerceLabApplication`

- [ ] **Step 1: bootstrap 의존성 추가**

`backend/bootstrap/build.gradle.kts`의 `dependencies` 블록에 추가:

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
```

- [ ] **Step 2: 실패하는 통합 테스트 작성**

`backend/bootstrap/src/test/kotlin/com/commercelab/bootstrap/HealthIntegrationTest.kt`:

```kotlin
package com.commercelab.bootstrap

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * H2가 아니라 실제 Postgres 컨테이너에 붙는다.
 *
 * 이유: M1에서 다룰 SELECT FOR UPDATE, advisory lock, 격리 수준은 H2에서
 * 동작이 다르거나 아예 없다. 테스트가 프로덕션과 다른 DB를 쓰면 락 관련
 * 테스트는 전부 거짓 안심이 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HealthIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("commerce")
            .withUsername("commerce")
            .withPassword("commerce")
            .withInitScript("db/init-schemas.sql")

        @DynamicPropertySource
        @JvmStatic
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `헬스 엔드포인트가 UP을 반환한다`() {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("commerce-lab"))
    }

    @Test
    fun `OpenAPI 문서가 생성된다`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/health']").exists())
    }

    @Test
    fun `order와 payment 스키마가 존재한다`() {
        postgres.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                val rs = statement.executeQuery(
                    "SELECT schema_name FROM information_schema.schemata " +
                        "WHERE schema_name IN ('order', 'payment') ORDER BY schema_name"
                )
                val schemas = mutableListOf<String>()
                while (rs.next()) {
                    schemas.add(rs.getString(1))
                }
                // kotlin의 assert()는 -ea 옵션이 꺼져 있으면 통째로 무시된다.
                // 테스트에서는 항상 assertEquals를 쓴다.
                assertEquals(listOf("order", "payment"), schemas)
            }
        }
    }
}
```

테스트용 초기화 스크립트도 필요하다.

`backend/bootstrap/src/test/resources/db/init-schemas.sql`:

```sql
CREATE SCHEMA IF NOT EXISTS "order";
CREATE SCHEMA IF NOT EXISTS payment;
```

주의: `infra/postgres/init/01-schemas.sql`과 내용이 겹친다. 지금은 중복을 허용한다 — Testcontainers는 compose와 독립적으로 떠야 하고, M1에서 Flyway를 도입하면 두 곳 모두 마이그레이션 파일 하나로 통합된다.

- [ ] **Step 3: 테스트 실패 확인**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew :bootstrap:test --tests '*HealthIntegrationTest*'
```

기대: 컴파일 실패 또는 컨텍스트 로딩 실패. `CommerceLabApplication`이 없다.

- [ ] **Step 4: 앱 진입점 작성**

`backend/bootstrap/src/main/kotlin/com/commercelab/bootstrap/CommerceLabApplication.kt`:

```kotlin
package com.commercelab.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 조립 지점.
 *
 * scanBasePackages를 com.commercelab로 넓히는 이유: order-core와 payment-core의
 * 빈을 여기서 모아 등록하기 위함이다. 모듈은 서로를 모르지만, bootstrap은 전부 안다.
 */
@SpringBootApplication(scanBasePackages = ["com.commercelab"])
class CommerceLabApplication

fun main(args: Array<String>) {
    runApplication<CommerceLabApplication>(*args)
}
```

- [ ] **Step 5: 헬스 컨트롤러 작성**

`backend/bootstrap/src/main/kotlin/com/commercelab/bootstrap/web/HealthController.kt`:

```kotlin
package com.commercelab.bootstrap.web

import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * actuator의 /actuator/health와 별개로 두는 이유: actuator 응답 형식은
 * 스프링이 소유하므로 OpenAPI 계약에 넣기 부적절하다. 프론트가 의존할
 * 계약은 우리가 소유하는 엔드포인트여야 한다.
 */
@RestController
@RequestMapping("/api")
class HealthController {

    @Operation(summary = "서비스 생존 확인")
    @GetMapping("/health")
    fun health(): HealthResponse = HealthResponse(status = "UP", service = "commerce-lab")
}

data class HealthResponse(
    val status: String,
    val service: String,
)
```

- [ ] **Step 6: 애플리케이션 설정 작성**

`backend/bootstrap/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: commerce-lab
  datasource:
    url: jdbc:postgresql://localhost:5432/commerce
    username: commerce
    password: commerce
  jackson:
    default-property-inclusion: non_null

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: commerce-lab

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html

logging:
  level:
    com.commercelab: DEBUG
```

- [ ] **Step 7: 테스트 통과 확인**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew :bootstrap:test --tests '*HealthIntegrationTest*'
```

기대: `BUILD SUCCESSFUL`, 3개 테스트 통과. (Testcontainers가 postgres:16-alpine 이미지를 처음 받으면 시간이 걸린다.)

- [ ] **Step 8: 전체 검증**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew build
```

기대: `BUILD SUCCESSFUL`. ArchUnit 5개 + Money 3개 + 통합 3개, 총 11개 테스트 통과.

- [ ] **Step 9: 실제 기동 확인**

인프라가 떠 있는 상태에서:

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew :bootstrap:bootRun &
sleep 30
curl -s http://localhost:8080/api/health
curl -s http://localhost:8080/actuator/prometheus | head -5
```

기대: `{"status":"UP","service":"commerce-lab"}` 와 메트릭 출력.

확인 후 백그라운드 프로세스를 종료한다: `kill %1`

- [ ] **Step 10: 커밋**

```bash
cd /Users/seokjuhong/workspace/commerce-lab
git add -A
git commit -m "feat: Boot 앱 진입점과 헬스 엔드포인트 추가

Testcontainers 기반 통합 테스트로 실제 Postgres에 붙는다.
H2를 쓰지 않는 이유는 M1의 락 테스트가 거짓 안심이 되지 않게 하기 위함이다."
```

---

## Task 5: 프론트엔드 뼈대와 API 타입 생성

**Files:**
- Create: `frontend/` (create-next-app 생성)
- Create: `frontend/src/app/providers.tsx`
- Modify: `frontend/src/app/layout.tsx`
- Modify: `frontend/src/app/page.tsx`
- Create: `frontend/src/lib/api.ts`
- Create: `frontend/src/types/api.d.ts` (생성 후 커밋)
- Modify: `frontend/package.json` (gen:api 스크립트)

**Interfaces:**
- Consumes: Task 4의 `GET /api/health`와 `GET /v3/api-docs`
- Produces:
  - `npm run gen:api` — 실행 중인 백엔드에서 OpenAPI를 읽어 `src/types/api.d.ts` 생성
  - `fetchHealth(): Promise<HealthResponse>` — `src/lib/api.ts` export
  - `http://localhost:3000` 에서 백엔드 상태를 표시하는 화면

- [ ] **Step 1: Next.js 프로젝트 생성**

```bash
cd /Users/seokjuhong/workspace/commerce-lab
npx --yes create-next-app@latest frontend \
  --typescript --tailwind --eslint --app --src-dir \
  --import-alias "@/*" --use-npm --yes
```

기대: `frontend/` 생성, `frontend/src/app/page.tsx` 존재

- [ ] **Step 2: 의존성 추가**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/frontend
npm install @tanstack/react-query
npm install --save-dev openapi-typescript
```

- [ ] **Step 3: 타입 생성 스크립트 추가**

`frontend/package.json`의 `scripts`에 추가:

```json
    "gen:api": "openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts"
```

- [ ] **Step 4: 백엔드 기동 후 타입 생성**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew :bootstrap:bootRun &
sleep 30

cd /Users/seokjuhong/workspace/commerce-lab/frontend
npm run gen:api
head -20 src/types/api.d.ts
```

기대: `src/types/api.d.ts`에 `/api/health` 경로와 `HealthResponse` 스키마가 포함된다.

백엔드는 Step 8까지 켜둔다.

- [ ] **Step 5: API 클라이언트 작성**

`frontend/src/lib/api.ts`:

```typescript
import type { components } from "@/types/api";

/**
 * 백엔드 OpenAPI에서 생성된 타입을 그대로 쓴다.
 *
 * 손으로 인터페이스를 다시 적지 않는 이유: 백엔드가 필드 이름을 바꾸면
 * 이 파일이 컴파일 에러를 낸다. API 계약 위반이 런타임이 아니라
 * 빌드 시점에 잡힌다.
 */
export type HealthResponse = components["schemas"]["HealthResponse"];

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch(`${BASE_URL}/api/health`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`헬스 체크 실패: ${response.status}`);
  }
  return response.json();
}
```

- [ ] **Step 6: TanStack Query Provider 작성**

`frontend/src/app/providers.tsx`:

```typescript
"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 5_000,
            retry: 1,
          },
        },
      }),
  );

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
```

`frontend/src/app/layout.tsx`의 `<body>` 안쪽을 `<Providers>`로 감싼다:

```typescript
import { Providers } from "./providers";

// ... 기존 metadata 유지 ...

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body className="antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
```

- [ ] **Step 7: 헬스 대시보드 화면 작성**

`frontend/src/app/page.tsx` 전체 교체:

```typescript
"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchHealth } from "@/lib/api";

export default function Home() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["health"],
    queryFn: fetchHealth,
    refetchInterval: 5_000,
  });

  return (
    <main className="min-h-screen bg-slate-950 text-slate-100 p-10">
      <div className="mx-auto max-w-2xl space-y-8">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold tracking-tight">commerce-lab</h1>
          <p className="text-slate-400">
            백엔드 학습 협업 프로젝트 — M0 스캐폴딩
          </p>
        </header>

        <section className="rounded-lg border border-slate-800 bg-slate-900 p-6">
          <h2 className="mb-4 text-sm font-medium uppercase tracking-wider text-slate-400">
            백엔드 상태
          </h2>

          {isLoading && <p className="text-slate-400">확인 중…</p>}

          {isError && (
            <div className="space-y-1">
              <p className="text-red-400">연결 실패</p>
              <p className="text-sm text-slate-500">
                {error instanceof Error ? error.message : "알 수 없는 오류"}
              </p>
              <p className="text-sm text-slate-500">
                백엔드가 켜져 있는지 확인하세요: ./gradlew :bootstrap:bootRun
              </p>
            </div>
          )}

          {data && (
            <dl className="grid grid-cols-2 gap-4">
              <div>
                <dt className="text-sm text-slate-500">status</dt>
                <dd className="text-lg font-medium text-emerald-400">{data.status}</dd>
              </div>
              <div>
                <dt className="text-sm text-slate-500">service</dt>
                <dd className="text-lg font-medium">{data.service}</dd>
              </div>
            </dl>
          )}
        </section>

        <section className="rounded-lg border border-slate-800 bg-slate-900 p-6">
          <h2 className="mb-3 text-sm font-medium uppercase tracking-wider text-slate-400">
            다음 마일스톤
          </h2>
          <p className="text-slate-300">
            M1 — 주문 코어와 동시성 제어. 이 화면에 상품 목록과 실시간 재고가 붙는다.
          </p>
        </section>
      </div>
    </main>
  );
}
```

- [ ] **Step 8: 빌드와 화면 확인**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/frontend
npm run build
```

기대: 타입 에러 없이 빌드 성공.

```bash
npm run dev &
sleep 10
curl -s http://localhost:3000 | grep -o "commerce-lab" | head -1
```

기대: `commerce-lab` 출력.

확인 후 프론트와 백엔드 프로세스를 모두 종료한다.

- [ ] **Step 9: 계약 위반 검출 역검증**

타입 생성이 실제로 계약을 지키는지 확인한다.

`frontend/src/lib/api.ts`에 임시로 추가:

```typescript
const _typeCheck: string = ({} as HealthResponse).nonExistentField;
```

```bash
cd /Users/seokjuhong/workspace/commerce-lab/frontend
npx tsc --noEmit
```

기대: **에러**. `Property 'nonExistentField' does not exist on type ...`

확인 후 임시 줄을 제거하고 `npx tsc --noEmit`을 다시 실행해 통과를 확인한다.

- [ ] **Step 10: 커밋**

```bash
cd /Users/seokjuhong/workspace/commerce-lab
git add -A
git commit -m "feat: Next.js 프론트엔드 뼈대와 OpenAPI 타입 생성 파이프라인

백엔드 OpenAPI에서 타입을 생성해 커밋한다.
API 계약이 깨지면 프론트 빌드가 실패하도록 만들어 계약 위반을 빌드 시점에 잡는다."
```

---

## Task 6: CI와 프로젝트 문서 정리

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `docs/PROGRESS.md`
- Create: `docs/adr/0001-modular-monolith.md` (형식 예시. 내용은 사용자가 M1에서 채운다)
- Modify: `CLAUDE.md` (§7 명령어를 실제 환경에 맞게 수정)
- Modify: `docs/superpowers/specs/2026-08-18-commerce-lab-design.md` (pnpm → npm, k6 실행 방식)
- Create: `README.md`

**Interfaces:**
- Consumes: Task 1~5의 모든 산출물
- Produces:
  - GitHub Actions 워크플로 — push/PR 시 백엔드 테스트 + 프론트 빌드
  - `docs/PROGRESS.md` — 마일스톤 체크리스트. 세션 시작 시 Claude가 읽는 상태 파일

- [ ] **Step 1: CI 워크플로 작성**

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  backend:
    name: 백엔드 테스트
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: JDK 21 설치
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - name: Gradle 캐시 설정
        uses: gradle/actions/setup-gradle@v4

      # Testcontainers는 러너의 Docker 데몬을 그대로 쓴다. 별도 services 블록이 필요 없다.
      - name: 아키텍처 규칙 검사
        working-directory: backend
        run: ./gradlew :bootstrap:archTest

      - name: 전체 테스트
        working-directory: backend
        run: ./gradlew build

      - name: 테스트 결과 업로드
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: backend-test-results
          path: backend/**/build/reports/tests/

  frontend:
    name: 프론트엔드 빌드
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Node 24 설치
        uses: actions/setup-node@v4
        with:
          node-version: "24"
          cache: npm
          cache-dependency-path: frontend/package-lock.json

      - name: 의존성 설치
        working-directory: frontend
        run: npm ci

      # 생성된 타입(src/types/api.d.ts)은 커밋돼 있으므로 백엔드 기동 없이 빌드된다.
      - name: 타입 검사
        working-directory: frontend
        run: npx tsc --noEmit

      - name: 빌드
        working-directory: frontend
        run: npm run build
```

주의: `archTest`는 `bootstrap` 프로젝트에만 등록돼 있으므로 반드시 `:bootstrap:archTest`로 부른다. 루트에는 같은 이름의 태스크가 없다.

- [ ] **Step 2: CI 문법 검증**

```bash
cd /Users/seokjuhong/workspace/commerce-lab
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('YAML OK')"
```

기대: `YAML OK`

- [ ] **Step 3: PROGRESS.md 작성**

`docs/PROGRESS.md`:

```markdown
# 진도

세션 시작 시 Claude가 가장 먼저 읽는 파일이다. 현재 위치와 다음 할 일을 여기서 판단한다.

## 현재 마일스톤

**M0 — 스캐폴딩** (Claude 전담) — 진행 중

## 마일스톤 체크리스트

### M0 — 스캐폴딩 (Claude)
- [ ] Gradle 멀티모듈 뼈대
- [ ] ArchUnit 아키텍처 규칙
- [ ] Docker Compose 인프라
- [ ] Boot 앱 + Testcontainers 통합 테스트
- [ ] 프론트엔드 뼈대 + OpenAPI 타입 생성
- [ ] CI + 문서

### M1 — 주문 코어와 동시성 제어 (사용자 구현)
- [ ] 설계문서 작성 (Claude)
- [ ] 1단계: 락 없이 구현 → 오버셀 관측
- [ ] 2단계: 낙관적 락 + 재시도 정책
- [ ] 3단계: 선점(HELD) + TTL, 만료/확정 경쟁 조건 처리
- [ ] 프론트: 상품 목록 / 주문 / 실시간 재고 (Claude)
- [ ] ADR 2건 이상 (사용자)
- [ ] 회고

### M2 — 결제 원장과 멱등성 (사용자 구현)
- [ ] 미시작

### M3 — 트랜잭셔널 아웃박스와 이벤트 (사용자 구현)
- [ ] 미시작

### M4 — 물리 분리와 Saga (사용자 구현)
- [ ] 미시작

## 배운 개념

마일스톤을 마칠 때마다 사용자가 채운다. 이직 서류의 원자료가 된다.

| 개념 | 마일스톤 | 한 줄 요약 |
|---|---|---|
| | | |
```

- [ ] **Step 4: ADR 템플릿 작성**

`docs/adr/0001-modular-monolith.md`:

```markdown
# ADR-0001: 모듈러 모놀리스로 시작하고 M4에서 분리한다

## 상태

채택 (2026-08-18)

## 배경

<!-- 사용자가 채운다: 어떤 문제 상황이었는지 -->

## 결정

<!-- 사용자가 채운다: 무엇을 하기로 했는지 -->

## 대안

<!-- 사용자가 채운다: 무엇을 버렸고 왜 -->

## 결과

<!-- 사용자가 채운다: 무엇을 얻고 무엇을 포기했는지 -->

---

**작성 안내:** 이 ADR은 사용자가 직접 채운다. Claude가 설명한 설계 의도를
자기 언어로 옮겨 적는 것이 이해했다는 증거이며, 면접 답변의 원본이 된다.
```

- [ ] **Step 5: CLAUDE.md 명령어 수정**

`CLAUDE.md`의 `## 7. 명령어` 섹션 전체를 아래로 교체한다 (pnpm → npm, k6 → Docker):

```markdown
## 7. 명령어

    # 인프라 기동 (Docker 데몬이 꺼져 있으면 open -a OrbStack 먼저)
    docker compose -f infra/docker-compose.yml up -d

    # 백엔드 전체 테스트
    cd backend && ./gradlew build

    # 아키텍처 규칙만 검사
    cd backend && ./gradlew :bootstrap:archTest

    # 백엔드 기동
    cd backend && ./gradlew :bootstrap:bootRun

    # 프론트 개발 서버 (npm 사용 — pnpm 미설치)
    cd frontend && npm run dev

    # API 타입 재생성 (백엔드가 켜져 있어야 함)
    cd frontend && npm run gen:api

    # 부하 테스트 (k6 설치 불필요, Docker 이미지 사용)
    docker run --rm -i --network host grafana/k6 run - < infra/k6/smoke.js

## 7-1. 포트

| 서비스 | 포트 |
|---|---|
| 백엔드 | 8080 |
| 프론트 | 3000 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Redpanda | 9092 |
| Prometheus | 9090 |
| Grafana | 3001 |
```

- [ ] **Step 6: 설계문서의 도구 표기 수정**

`docs/superpowers/specs/2026-08-18-commerce-lab-design.md`에서:

- `TanStack Query + Tailwind | OpenAPI 타입 생성으로` 행은 그대로 둔다.
- §2 기술 스택 표의 부하 행 `k6 | 시나리오를 코드로 관리, CI 연동 가능` 을 `k6 (Docker 이미지) | 로컬 설치 없이 실행. 시나리오를 코드로 관리` 로 바꾼다.
- 문서 하단에 다음 문단을 추가한다:

```markdown
## 12. 도구 실행 환경 (M0에서 확정)

- 패키지 매니저는 npm을 쓴다 (pnpm 미설치).
- k6는 설치하지 않고 Docker 이미지 `grafana/k6`로 실행한다.
- Gradle CLI가 없으므로 모든 빌드는 `./gradlew` 래퍼로 한다 (Gradle 8.14.3).
- Grafana는 3001 포트를 쓴다. 프론트엔드가 3000을 점유하기 때문이다.
- jOOQ는 M0 범위에서 제외했다. 조회 최적화가 실제로 필요해지는 M1 3단계에서 도입한다.
```

- [ ] **Step 7: README 작성**

`README.md`:

```markdown
# commerce-lab

백엔드 실력을 실전 수준으로 끌어올리기 위한 학습 협업 프로젝트.

주문 → 결제(복식부기 원장) → 배송 흐름을 Kotlin/Spring 모듈러 모놀리스로 구현하고,
동시성 제어 · 멱등성 · 이벤트 정합성 · 분산 트랜잭션을 단계적으로 다룬다.

## 역할

| 영역 | 담당 |
|---|---|
| 백엔드 프로덕션 코드 | 사람 |
| 백엔드 설계 · 실패 테스트 · 코드리뷰 | Claude |
| 프론트엔드 · 인프라 · CI | Claude |

상세 규칙은 [CLAUDE.md](./CLAUDE.md)에 있다.

## 문서

- [설계문서](./docs/superpowers/specs/2026-08-18-commerce-lab-design.md)
- [진도](./docs/PROGRESS.md)
- [ADR](./docs/adr/)

## 빠르게 실행하기

    # 1. 인프라
    docker compose -f infra/docker-compose.yml up -d

    # 2. 백엔드
    cd backend && ./gradlew :bootstrap:bootRun

    # 3. 프론트엔드 (새 터미널)
    cd frontend && npm install && npm run dev

http://localhost:3000 에서 백엔드 상태를 확인할 수 있다.

## 마일스톤

| | 주제 | 핵심 학습 |
|---|---|---|
| M0 | 스캐폴딩 | 모듈 경계 강제, 테스트 인프라 |
| M1 | 주문 코어 | 동시성 제어, 락, 선점과 TTL |
| M2 | 결제 원장 | 멱등성, 복식부기, 정합성 검증 |
| M3 | 아웃박스 | at-least-once, 컨슈머 멱등성, DLQ |
| M4 | 서비스 분리 | Saga, 보상 트랜잭션, 분리 비용 측정 |
```

- [ ] **Step 8: 전체 검증**

```bash
cd /Users/seokjuhong/workspace/commerce-lab/backend
./gradlew build

cd /Users/seokjuhong/workspace/commerce-lab/frontend
npx tsc --noEmit && npm run build
```

기대: 양쪽 모두 성공.

- [ ] **Step 9: PROGRESS.md의 M0 항목 체크**

`docs/PROGRESS.md`의 M0 체크박스 6개를 모두 `- [x]`로 바꾸고, 현재 마일스톤을 아래로 수정한다:

```markdown
## 현재 마일스톤

**M1 — 주문 코어와 동시성 제어** — 설계문서 작성 대기 (Claude)
```

- [ ] **Step 10: 커밋과 푸시**

```bash
cd /Users/seokjuhong/workspace/commerce-lab
git add -A
git commit -m "ci: GitHub Actions 워크플로와 프로젝트 문서 추가

백엔드 테스트(Testcontainers 포함)와 프론트 타입 검사·빌드를 CI에서 검증한다.
CLAUDE.md의 명령어를 실제 환경(npm, Docker k6)에 맞게 수정했다."
git push origin main
```

- [ ] **Step 11: CI 통과 확인**

```bash
sleep 60
gh run list --limit 1
gh run watch --exit-status
```

기대: 두 잡(backend, frontend) 모두 성공.

실패하면 `gh run view --log-failed`로 원인을 확인하고 수정 후 재푸시한다.

---

## 완료 기준

M0가 끝났다고 말할 수 있으려면 아래가 전부 참이어야 한다.

- [ ] `docker compose -f infra/docker-compose.yml up -d` 후 5개 서비스가 정상 기동
- [ ] `cd backend && ./gradlew build` 성공 — 테스트 11개 통과
- [ ] `cd backend && ./gradlew :bootstrap:archTest` 성공 — 불변 규칙 5개 강제됨
- [ ] ArchUnit 규칙이 실제 위반을 잡는 것을 역검증으로 확인함
- [ ] `http://localhost:3000` 에서 백엔드 상태가 `UP`으로 표시됨
- [ ] 프론트 타입이 백엔드 OpenAPI에서 생성되며, 계약 위반 시 `tsc`가 실패함
- [ ] GitHub Actions CI 초록
- [ ] 새 머신에서 clone 후 위 명령들이 그대로 동작 (Gradle 래퍼 커밋됨)

## M0 이후

다음 산출물은 `docs/milestones/M1-order-core.md`다. 여기에는:

1. 주문 상태머신과 재고 모델의 설계 의도
2. 사용자가 구현할 인터페이스 시그니처 (구현체 없음)
3. 실패하는 테스트 — 3단계(무락 → 낙관적 락 → 선점) 각각의 테스트
4. 만료/확정 경쟁 조건을 재현하는 통합 테스트
5. k6 부하 시나리오 (오버셀 관측용)
6. 설계 논쟁 질문 1건

이 문서를 쓰는 것이 M1의 시작이며, 그 시점부터 프로덕션 코드는 사용자가 쓴다.
