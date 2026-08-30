# 도메인 객체의 탄생과 복원 — 팩토리, 의존 방향, 그리고 Kotlin의 함정

> Phase 2 Step 2에서 `Member` 생성자를 private으로 막으며 부딪힌 세 가지 질문.
> ① 도메인이 `PasswordHasher`를 쓰려면 어느 패키지에 둬야 하나
> ② 생성자를 막았는데 DB에서 읽은 걸 어떻게 되살리나
> ③ `data class`의 `copy()`가 private 생성자를 우회한다?

## 1. 도메인이 필요로 하는 인터페이스는 도메인이 소유한다

### 상황

`Member.register(email, rawPassword, hasher)` — 도메인 팩토리가 비밀번호를 해싱해야 한다.
그런데 `PasswordHasher`가 `application/port/out`에 있으면:

```
domain  ──import──▶  application     ❌ 안쪽이 바깥쪽을 참조
```

헥사고날의 제1 규칙 "의존은 바깥 → 안쪽"이 깨진다. `domain`은 아무것도 의존하지 않아야 한다.

### 해결: 인터페이스를 `domain`으로 이동

```
modules/member/domain/
  Member.kt
  PasswordHasher.kt      ← 인터페이스. "도메인이 외부에 요구하는 능력"의 선언
modules/member/adapter/out/security/
  BCryptPasswordHasher.kt   ← 구현. 여전히 바깥 → 안쪽 방향
```

의존 방향은 그대로다 — **인터페이스는 안쪽에, 구현은 바깥쪽에**. 바뀐 건 인터페이스의 위치뿐이다.
"port/out은 application에 둔다"는 건 관례이지 법칙이 아니다. 판단 기준은 하나:
**그 인터페이스를 누가 사용하는가.** 서비스만 쓰면 application, 도메인 객체가 쓰면 domain.

### DDD 용어: 도메인 서비스 (Domain Service)

"해싱"처럼 **특정 엔티티의 책임은 아니지만 도메인 규칙의 일부인 동작**을 인터페이스로 뽑은 것.
`Member`가 해싱 알고리즘을 알 필요는 없지만, "비밀번호는 해싱되어야 회원이 될 수 있다"는 도메인 규칙이다.
그래서 인터페이스(규칙)는 도메인이 갖고, 알고리즘(BCrypt)은 어댑터가 갖는다.

이것이 **의존성 역전(DIP)** 의 도메인 버전이다. Phase 1의 `ProductRepository`(application 소유, 서비스가 사용)와
원리는 같고 소유자만 다르다.

### 판단 체크리스트

| 인터페이스 | 사용자 | 위치 |
|---|---|---|
| `ProductRepository` | `ProductService` | `application/port/out` |
| `TokenIssuer` | `AuthService` (예정) | `application/port/out` |
| `PasswordHasher` | `Member.register()` | **`domain`** |

## 2. 생성(create) vs 복원(reconstitute)

### 상황

생성자를 `private`으로 막고 `Member.register()`만 열었더니, JPA 어댑터의 `toDomain()`이 컴파일되지 않는다.
DB에서 읽은 데이터로 `Member`를 만들 방법이 없다.

### 핵심 인식: 도메인 객체가 태어나는 경로는 두 가지다

| 경로 | 언제 | 비즈니스 규칙 |
|---|---|---|
| **생성 (create)** | 새 회원이 처음 태어날 때 | **전부 적용** — 평문 길이 검사, 해싱, 기본 Role 부여 |
| **복원 (reconstitute)** | 저장소에서 읽어 메모리에 되살릴 때 | **재적용 안 함** — 저장 시점에 이미 통과했다 |

복원 경로에서 `register()`를 다시 타면 "이미 해시된 비밀번호를 또 해싱"하는 식의 오작동이 난다.
두 경로는 **다른 팩토리**여야 한다.

```kotlin
data class Member private constructor(
    val id: Long?,
    val email: Email,
    val hashedPassword: HashedPassword,
    val name: String,
    val role: Role,
) {
    companion object {
        const val PASSWORD_MIN_LENGTH = 8

        /** 새 회원 생성. 모든 비즈니스 규칙을 적용한다. */
        fun register(email: Email, rawPassword: String, hasher: PasswordHasher, name: String): Member {
            require(rawPassword.length >= PASSWORD_MIN_LENGTH) { "비밀번호는 ${PASSWORD_MIN_LENGTH}자 이상이어야 합니다." }
            return Member(null, email, HashedPassword(hasher.hash(rawPassword)), name, Role.CUSTOMER)
        }

        /** 저장소 전용. 이미 검증된 상태를 그대로 되살린다 — 규칙을 다시 적용하지 않는다. */
        fun reconstitute(id: Long, email: Email, hashedPassword: HashedPassword, name: String, role: Role): Member =
            Member(id, email, hashedPassword, name, role)
    }
}
```

어댑터 쪽:

```kotlin
fun MemberJpaEntity.toDomain(): Member = Member.reconstitute(
    id = requireNotNull(id) { "저장된 엔티티는 id가 있어야 합니다" },   // DB에서 왔으니 non-null
    email = Email(email),
    hashedPassword = HashedPassword(password),
    name = name,
    role = role,
)
```

`reconstitute`의 `id`가 `Long`(non-null)인 점에 주목 — 복원되는 회원은 반드시 id가 있다. 경로마다 타입으로 사실을 못박는다.

### "reconstitute를 아무나 부르면 규칙 우회 아닌가?"

맞다. 이건 **컴파일러가 아니라 이름과 리뷰로 지키는 경계**다. 이름이 "복원용"이라고 말하고 있고,
저장소 어댑터(및 테스트의 Fake 저장소) 밖에서 호출되면 코드 리뷰에서 잡는다.
완벽한 봉인 대신 **의도가 드러나는 이름**으로 타협하는 것이 실무 표준이다.

### 완성된 순환

```
서비스   → Member.register(...)       새 회원 (규칙 전부 적용)
저장소   → Member.reconstitute(...)   저장된 회원 되살리기 (규칙 재적용 없음)
누구도   → Member(...)                생성자 직접 호출 불가
```

"유효하지 않은 Member는 register를 통과 못 하고 → register를 통과한 것만 DB에 있고 → DB에서 온 것은 reconstitute로 되살아난다."
[02-validation-layers.md](02-validation-layers.md)의 always-valid domain model이 이 순환으로 완결된다.

## 3. Kotlin 함정: `data class`의 `copy()`는 private 생성자를 우회한다

### 문제

```kotlin
data class Member private constructor(val hashedPassword: HashedPassword, ...)

member.copy(hashedPassword = HashedPassword("평문그대로"))   // ✅ 컴파일됨 — 생성자가 private인데!
```

`data class`가 자동 생성하는 `copy()`는 **항상 public**이었다 (Kotlin 2.0.19까지). 생성자를 아무리 막아도
`copy()`를 통해 임의의 상태를 가진 인스턴스를 만들 수 있다 — 팩토리로 지키려던 불변식이 새어나간다.
Kotlin 팀도 이를 설계 실수로 인정했다.

### 해결: `@ConsistentCopyVisibility`

```kotlin
@ConsistentCopyVisibility   // copy()의 가시성을 생성자와 일치시킨다 (Kotlin 2.0.20+)
data class Member private constructor(...)
```

이제 `copy()`도 private이 되어 클래스 밖에서 호출할 수 없다. 상태 변경은 도메인 메서드로만:

```kotlin
fun promoteToAdmin(): Member = copy(role = Role.ADMIN)   // 클래스 안에서는 copy 사용 가능
```

### 파급 효과

테스트의 `FakeMemberRepository`가 `member.copy(id = id)`로 id를 채우고 있었다면 컴파일 에러가 난다.
→ Fake도 "저장소"이므로 `Member.reconstitute(id, ...)`를 쓰는 것이 의미상 맞다.

### 그래서 data class를 도메인 엔티티에 써도 되나?

논쟁이 있다. `equals`/`hashCode`가 **모든 필드 기준**이라는 것도 엔티티(id로 동일성 판단)와는 맞지 않는다.
실무 선택지:
- **data class + `@ConsistentCopyVisibility`**: `copy`의 편리함 유지, 불변식 보호. 이 프로젝트의 선택
- **일반 class + 명시적 `equals`(id 기준)**: DDD 원칙에 충실. 보일러플레이트 증가
- 값 객체(`Email`, `Money`)는 고민 없이 data/value class — 값 동등성이 원래 맞다

## 면접 예상 질문

**Q. 도메인 계층이 외부 기능(해싱, 시각, ID 생성)을 필요로 할 때 어떻게 하나요?**
→ 도메인에 인터페이스를 두고 어댑터가 구현한다(DIP). 의존 방향은 여전히 바깥→안쪽. DDD의 도메인 서비스 개념.

**Q. 생성자를 막고 팩토리만 열면 ORM이 객체를 어떻게 만드나요?**
→ ORM은 JPA 엔티티(별도 클래스)를 다루고, 도메인 객체는 어댑터가 `reconstitute` 팩토리로 되살린다. 생성과 복원은 규칙 적용 여부가 다르므로 팩토리를 분리한다.

**Q. Kotlin data class를 도메인 모델로 쓸 때 주의점은?**
→ `copy()`가 private 생성자를 우회한다(`@ConsistentCopyVisibility`로 해결), `equals`가 전체 필드 기준이라 엔티티 동일성과 다르다, JPA 엔티티로는 쓰지 않는다(val/불변 문제).

## 연결 문서

- [02-validation-layers.md](02-validation-layers.md) — always-valid domain model
- [04-value-objects.md](04-value-objects.md) — Email/HashedPassword 값 객체
- [ARCHITECTURE.md](../ARCHITECTURE.md) — 헥사고날 의존 방향
