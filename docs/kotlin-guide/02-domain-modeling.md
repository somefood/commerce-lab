# Kotlin 가이드 2편 — Phase 2에서 만나는 문법 (도메인 모델링 + 설정)

1편이 "읽고 쓰기 위한 최소 문법"이었다면, 2편은 **도메인을 표현력 있게 만드는 도구들**이다.

## 1. value class — 원시값에 타입을 입힌다

`email: String`은 어떤 문자열이든 받는다. "이메일"이라는 개념을 타입으로 만들면 컴파일러가 실수를 잡아준다.

```kotlin
@JvmInline
value class Email(val value: String) {
    init {
        require(value.contains("@")) { "이메일 형식이 아닙니다: $value" }
    }
}

fun findByEmail(email: Email)   // String을 넘기면 컴파일 에러 — 이름과 이메일을 바꿔 넣는 사고 방지
```

`@JvmInline value class`는 런타임에 래퍼 객체 없이 String으로 컴파일된다 (성능 비용 0). Java의 "원시값 집착(Primitive Obsession)" 안티패턴을 해소하는 Kotlin의 답. JPA 엔티티에 직접 쓰기엔 아직 불편하니 **도메인에서만** 쓰고 어댑터에서 `.value`로 풀어라.

## 2. sealed class / sealed interface — "이 중 하나"를 타입으로

로그인 결과는 성공 아니면 실패인데, 실패에도 종류가 있다. 예외 대신 타입으로 표현하면 `when`이 누락을 잡아준다.

```kotlin
sealed interface LoginResult {
    data class Success(val token: String) : LoginResult
    data object InvalidCredentials : LoginResult   // 상태 없는 케이스는 data object
    data object Deactivated : LoginResult
}

val message = when (result) {          // else 없음 — 새 케이스가 생기면 여기서 컴파일 에러
    is LoginResult.Success -> "환영합니다"
    LoginResult.InvalidCredentials -> "이메일 또는 비밀번호가 올바르지 않습니다"
    LoginResult.Deactivated -> "비활성화된 계정입니다"
}
```

`is`는 타입 검사 + 자동 캐스팅(smart cast) — 그 분기 안에서 `result.token`을 바로 쓸 수 있다.
Phase 2에서 강제는 아니지만, 예외 대신 결과 타입을 쓰면 어떤 느낌인지 한 번 시도해볼 가치가 있다.

## 3. enum class에 데이터/동작 붙이기

```kotlin
enum class Role(val authority: String) {
    CUSTOMER("ROLE_CUSTOMER"),
    ADMIN("ROLE_ADMIN");

    fun isAdmin() = this == ADMIN
}
```

Spring Security는 권한 문자열에 `ROLE_` 접두어를 기대한다. 그 변환 규칙을 enum이 갖고 있으면 여기저기서 문자열을 조립하지 않아도 된다.

## 4. companion object — 정적 팩토리와 상수

Kotlin엔 `static`이 없다. 클래스에 하나 붙는 동반 객체가 그 자리를 대신한다.

```kotlin
class Member private constructor(...) {     // 생성자를 감추고
    companion object {
        const val PASSWORD_MIN_LENGTH = 8   // const val = 컴파일 타임 상수 (Java의 static final)

        fun register(email: Email, rawPassword: String, hasher: PasswordHasher): Member {
            require(rawPassword.length >= PASSWORD_MIN_LENGTH) { "..." }
            return Member(email, hasher.hash(rawPassword), Role.CUSTOMER)   // 팩토리로만 생성 가능
        }
    }
}
```

`Product.create()`에서 이미 썼다. 이번엔 **생성자를 private으로 막고** 팩토리만 열어보자 — "해싱 안 된 비밀번호로 Member가 만들어지는 일"을 컴파일 타임에 불가능하게 만든다 (always-valid의 강화판).

## 5. 확장 함수 — 남의 클래스에 메서드 붙이기

```kotlin
fun Jwt.memberId(): Long = subject.toLong()          // Spring의 Jwt 클래스에 메서드를 "추가"
fun Jwt.role(): Role = Role.valueOf(getClaimAsString("role"))

// 컨트롤러에서
fun me(@AuthenticationPrincipal jwt: Jwt) = getMemberQuery.getMember(jwt.memberId())
```

Phase 1의 `Product.toEntity()`가 이미 확장 함수였다. 실제로는 정적 메서드로 컴파일되므로 클래스를 건드리지 않는다. "이 클래스에 이 기능이 있었으면" 할 때 상속/유틸 클래스 대신 쓰는 게 Kotlin 관용구.

## 6. 설정값 주입 — @ConfigurationProperties

```kotlin
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val expirationSeconds: Long = 3600,   // 기본값 가능
)
// 활성화: @SpringBootApplication 클래스에 @ConfigurationPropertiesScan
```

```yaml
jwt:
  secret: ${JWT_SECRET:local-dev-secret-must-be-at-least-32-bytes-long!!}
  expiration-seconds: 3600     # kebab-case ↔ camelCase 자동 매핑
```

`@Value("\${jwt.secret}")`를 여기저기 뿌리는 것보다 타입 안전하고 테스트에서 객체로 바로 만들 수 있다. `${JWT_SECRET:기본값}` 문법은 "환경변수 있으면 그거, 없으면 기본값".

## 7. 예외 클래스 정의

```kotlin
class DuplicateEmailException(email: String) :
    RuntimeException("이미 가입된 이메일입니다: $email")
```

Kotlin은 checked exception이 없다 — 전부 unchecked. `throws` 선언도 없다. 그래서 "어느 예외를 어디서 잡는가"를 설계로 정해야 한다 (Phase 1의 `@RestControllerAdvice`가 그 자리).

## 8. lateinit vs by lazy (테스트에서 자주 만남)

```kotlin
@Autowired lateinit var mockMvc: MockMvc        // "나중에 반드시 초기화됨" — 스프링 주입용
private val objectMapper by lazy { ObjectMapper() }  // 처음 접근할 때 한 번만 생성
```

`lateinit`은 초기화 전에 접근하면 명확한 예외(`UninitializedPropertyAccessException`)를 던진다 — NPE보다 원인이 분명하다.

---

**다음 편 예고 (Phase 3)**: 제네릭과 `Result<T>`, 컬렉션 고급(`groupBy`, `fold`), `sealed`로 주문 상태 머신 표현, `inline`/`reified`.
