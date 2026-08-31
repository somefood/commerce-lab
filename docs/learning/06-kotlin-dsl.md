# Kotlin DSL — 코드가 설정 파일처럼 읽히는 이유

> Phase 2 Step 3에서 `SecurityFilterChain`을 Kotlin DSL로 작성하다 나온 질문:
> "`http { csrf { disable() } }` 이게 어떻게 동작하는 문법이야?" + IDE가 엉뚱한 import를 주워온 사고의 원인.

## 1. DSL이란

**Domain-Specific Language (도메인 특화 언어)** — 범용 언어(Kotlin, Java)와 달리 **특정 목적 하나**를 위해 설계된 미니 언어.

| DSL | 목적 |
|---|---|
| SQL | 데이터 질의 |
| 정규식 | 문자열 패턴 매칭 |
| HTML | 문서 구조 |
| `build.gradle.kts` | 빌드 설정 |

Kotlin에서 "DSL"이라고 하면 보통 **내부(embedded) DSL**을 말한다: 별도 언어를 만드는 게 아니라,
**평범한 Kotlin 코드가 설정 파일처럼 읽히도록 라이브러리를 설계하는 기법**이다.
컴파일러가 문법을 검사해주고, IDE 자동완성이 되고, 변수/조건문을 섞어 쓸 수 있다는 게 외부 DSL(XML 등) 대비 장점.

```kotlin
// 우리가 이미 매일 쓰고 있는 Kotlin DSL들
plugins { kotlin("jvm") }                          // Gradle
http { csrf { disable() } }                        // Spring Security
mockMvc.perform(post("/api/members")) { ... }      // (MockMvc kotlin 확장도 있음)
```

## 2. 마법의 재료는 딱 세 개

### 재료 ① 후행 람다 (trailing lambda)

마지막 인자가 람다면 괄호 밖으로 뺄 수 있고, 유일한 인자면 괄호도 생략된다.

```kotlin
fun csrf(block: () -> Unit) { ... }

csrf({ println("hi") })   // 원형
csrf() { println("hi") }  // 람다를 밖으로
csrf { println("hi") }    // 괄호 생략 → 언어 키워드처럼 보인다
```

### 재료 ② 수신 객체 지정 람다 (lambda with receiver) — 핵심

람다 타입을 `() -> Unit`이 아니라 **`CsrfDsl.() -> Unit`** 로 선언하면,
그 블록 안에서 `this`가 `CsrfDsl` 인스턴스가 된다.

```kotlin
class CsrfDsl {
    fun disable() { ... }
}

fun csrf(block: CsrfDsl.() -> Unit) {
    val dsl = CsrfDsl()
    dsl.block()          // dsl을 this로 삼아 블록 실행
}

csrf {
    disable()            // == this.disable() == 그 CsrfDsl의 disable()
}
```

**`apply { }`와 완전히 같은 원리다.** `apply`의 시그니처가 `T.() -> Unit`이라서 블록 안에서
객체 프로퍼티를 바로 쓸 수 있었던 것 (Phase 1의 `apply { copy(...) }` 함정도 이 문법이 원인이었다 —
블록 안 이름이 바깥 변수가 아니라 수신 객체의 프로퍼티로 먼저 해석된다).

### 재료 ③ `invoke` 연산자

`operator fun invoke(...)`가 정의된 객체는 **함수처럼 호출**할 수 있다.

```kotlin
operator fun HttpSecurity.invoke(config: HttpSecurityDsl.() -> Unit) { ... }

http { ... }   // == http.invoke({ ... }) — 변수를 함수처럼 부른 것
```

Spring Security가 이걸 **확장 함수**로 제공하기 때문에, import가 필요하다:

```kotlin
import org.springframework.security.config.annotation.web.invoke
```

## 3. 실전 사고: import 하나가 없으면 IDE가 쓰레기를 주워온다

`invoke` import가 없으면 Kotlin은 `http { ... }`라는 문법 자체를 해석할 수 없다.
그러면 IDE는 블록 안의 `disable()`, `permitAll` 같은 이름을 **클래스패스 전체에서 이름만 맞는 아무거나**로 자동 import 한다.

실제로 겪은 사고 (Phase 2 Step 3):

```kotlin
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory.disable   // Tomcat 내부 유틸!
import org.springframework.security.authorization.SingleResultAuthorizationManager.permitAll
import org.springframework.security.config.web.server.ServerHttpSecurity.http   // WebFlux(리액티브)용!
```

**의심 신호**: 자동 import에 `org.apache.catalina`, `...web.server...`(리액티브), 전혀 무관한 클래스의
정적 멤버가 보이면 즉시 멈추고 원래 필요한 확장 함수 import를 찾을 것.
확장 함수는 클래스 멤버가 아니라서 **import 해야만 존재가 보인다** — DSL이 안 먹으면 십중팔구 import 문제다.

## 4. 직접 만들어보기 — 미니 Security DSL

재료 세 개를 조립하면 Spring Security DSL의 미니어처를 30줄로 만들 수 있다.

```kotlin
// ── 설정을 담을 평범한 클래스들 ──────────────────────────
class ServerConfig {
    var port: Int = 8080
    val allowedPaths = mutableListOf<String>()
    var csrfEnabled = true
}

// ── DSL 클래스: 블록 안에서 this가 될 객체 ───────────────
class ServerDsl(private val config: ServerConfig) {

    var port: Int
        get() = config.port
        set(value) { config.port = value }          // 재료②: 프로퍼티 대입도 DSL 문법이 된다

    fun csrf(block: CsrfDsl.() -> Unit) {           // 재료①+②: 중첩 블록
        CsrfDsl(config).block()
    }

    fun allow(path: String) {
        config.allowedPaths += path
    }
}

class CsrfDsl(private val config: ServerConfig) {
    fun disable() { config.csrfEnabled = false }
}

// ── 진입점: 최상위 함수 (Security는 invoke 확장 함수를 썼다) ──
fun server(block: ServerDsl.() -> Unit): ServerConfig {
    val config = ServerConfig()
    ServerDsl(config).block()
    return config
}
```

사용하면 이렇게 읽힌다:

```kotlin
val config = server {
    port = 8081
    csrf { disable() }
    allow("/api/products")
    allow("/api/members")
}
// config.port == 8081, config.csrfEnabled == false
```

각 줄이 어떻게 해석되는지 대응표:

| DSL 문법 | 실제 실행되는 것 |
|---|---|
| `server { ... }` | `server({ ... })` — 후행 람다 |
| `port = 8081` | `this.port = 8081` — ServerDsl의 setter → config에 기록 |
| `csrf { disable() }` | `this.csrf({ ... })` → 새 `CsrfDsl`을 this로 삼아 블록 실행 |
| `disable()` | `CsrfDsl.disable()` → config에 기록 |

**본질: DSL은 "블록을 실행하면서 설정 객체를 채워나가는 빌더"를 예쁜 문법으로 포장한 것.**
Spring Security의 `http { }`도 내부에서 `HttpSecurity`라는 빌더를 채운 뒤 `build()`로
`SecurityFilterChain`을 만드는, 정확히 같은 구조다.

### 덤: `@DslMarker`

중첩 DSL에서 바깥 블록의 메서드가 안쪽 블록에서 보이는 문제(`csrf { allow("...") }`가 컴파일되는 것)를
막는 애너테이션. 라이브러리 만들 때의 이야기라 지금은 "이런 게 있다"만 알면 충분.

## 면접 예상 질문

**Q. Kotlin DSL은 어떤 언어 기능으로 만들어지나요?**
→ 후행 람다(마지막 람다 인자를 괄호 밖으로), 수신 객체 지정 람다(`T.() -> Unit` — 블록 안 this가 T),
`invoke` 연산자/확장 함수. Gradle Kotlin DSL, Spring Security DSL이 대표 사례.

**Q. 수신 객체 지정 람다가 뭔가요?**
→ 람다 타입이 `T.() -> Unit`이면 람다 본문에서 `this`가 T 인스턴스가 된다. `apply`/`with`가 이걸로 구현되어 있고,
DSL 블록 안에서 함수를 접두어 없이 부를 수 있는 이유다.

**Q. Java 설정 방식 대비 Kotlin DSL의 장단점은?**
→ 장점: 읽기 쉬움, 타입 안전(컴파일러 검사), IDE 자동완성, 조건문/변수 혼용 가능.
단점: 확장 함수 import 누락 시 오류 메시지가 난해함, 스코프 안에서 이름 해석이 겹치면(스코프 함수 함정) 디버깅이 어려움.

## 연결 문서

- [kotlin-guide/01-essentials.md](../kotlin-guide/01-essentials.md) — 스코프 함수(`apply`/`let`)와 수신 객체
- [05-domain-object-lifecycle.md](05-domain-object-lifecycle.md) — companion 팩토리 (DSL과 함께 "생성을 제어하는" Kotlin 관용구)
- [03-auth-primer.md](03-auth-primer.md) — 이 DSL로 설정하는 Security 개념들
