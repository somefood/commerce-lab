# 값 객체와 Primitive Obsession

> Phase 2 시작 시 나온 질문: "Primitive Obsession이 뭐야?", "`@JvmInline value class`를 왜 써야 해?"

## 1. Primitive Obsession (원시값 집착)

**도메인 개념을 전용 타입 없이 String, Int, Long 같은 원시 타입으로 때우는 습관.**
마틴 파울러 『리팩터링』의 코드 냄새(code smell) 중 하나.

### 증상

```kotlin
data class Member(
    val email: String,      // 이메일도 String
    val name: String,       // 이름도 String
    val password: String,   // 해시된 비밀번호도 String
)
```

컴파일러에게 세 필드는 전부 "그냥 String"이다. 그래서 이런 사고를 못 막는다:

```kotlin
Member(email = name, name = email, password = rawPassword)   // 자리 바뀜 — 컴파일 통과
memberRepository.findByEmail(member.name)                     // 이름을 이메일 자리에 — 컴파일 통과
```

### 왜 "집착"이라 부르나

원시 타입으로 때우면 그 개념에 딸린 **규칙과 동작이 갈 곳을 잃는다**:
- "이메일 형식이어야 한다" → Request DTO, 서비스, 테스트 픽스처… 이메일이 등장하는 **모든 곳에 검증이 흩어짐**
- "대소문자 무시 비교" → 비교하는 곳마다 `lowercase()` 반복
- 결국 규칙이 코드 곳곳에 복붙된다

### Phase 1에도 있었다

`Product.price: Long` — 가격이 Long이면 `price + stockQuantity`(가격에 재고를 더하는 무의미한 연산)가 컴파일된다.
`Money` 값 객체가 있으면 막히고, 통화·할인 같은 동작이 붙을 자리도 생긴다. → Phase 3(주문)에서 `Money` 도입 예정.

## 2. 해소: 값 객체 (Value Object)

개념에 타입을 주면 규칙이 **한 곳에** 모인다.

```kotlin
@JvmInline
value class Email(val value: String) {
    init {
        require(value.contains("@")) { "이메일 형식이 아닙니다: $value" }
    }
}

fun findByEmail(email: Email)   // name을 넣으면 컴파일 에러
```

효과:
1. 잘못된 값은 **생성 자체가 안 된다** (always-valid — [02-validation-layers.md](02-validation-layers.md) 참조)
2. 자리 바꿔치기 실수를 **컴파일러가** 잡는다
3. 규칙이 한 곳에 모여 중복이 사라진다

### 값 객체의 성질 (DDD 용어)
- **식별자가 없다**: `Email("a@b.com")` 두 개는 같은 것. 엔티티(id로 구분)와의 차이
- **불변**: 바꾸려면 새로 만든다 (`copy`)
- **동등성은 값으로**: 내용이 같으면 같다 (`==`)

## 3. `@JvmInline value class` — 왜 이 두 키워드인가

### `value class`: "값 하나를 감싼 껍데기"라는 선언

일반 `class Email(val value: String)`은 **런타임에 진짜 객체**가 생긴다 (힙 할당 + String 참조).
`value class`는 컴파일러에게 *"String에 타입 이름표만 붙인 거니 런타임엔 String으로 취급해"*라고 말하는 것.

```kotlin
val email = Email("a@b.com")   // 컴파일 후: Email 객체 없음, String만 존재
fun send(email: Email)         // 컴파일 후: fun send(email: String)
```

타입 검사는 컴파일 타임에만, **런타임 비용 0**. 이 최적화가 **인라이닝**이고, 그래서 예전 이름이 `inline class`였다.

대가로 제약: 프로퍼티 **딱 하나**, 추가 상태 불가, 상속 불가. "값 하나를 감싼다"는 목적에 맞춘 제약.

### `@JvmInline`: "JVM에서 인라이닝해도 된다"는 확인 도장

Kotlin은 JVM 외 JS/Native로도 컴파일되며, 값 클래스의 인라이닝 방식이 플랫폼마다 다를 수 있다.
또 JVM 자체가 **Project Valhalla**로 "진짜 값 타입"을 지원할 예정이라, *"지금은 컴파일러 트릭이고 나중에 JVM 네이티브 지원으로 바뀔 수 있다"*는 걸 명시하도록 요구한다.

→ **현재 JVM에서는 `@JvmInline` 없이 `value class`를 쓰면 컴파일 에러.** 둘은 세트.

## 4. value class vs data class — 실전 선택

| | 런타임 객체 | 장점 | 단점 |
|---|---|---|---|
| `@JvmInline value class` | **없음** (원시값으로 컴파일) | 비용 0, 대량 데이터에 유리 | JPA/Jackson 등 리플렉션 기반 라이브러리와 마찰 (필드명 맹글링 `getValue-xxxx()`) |
| `data class Email(val value: String)` | 있음 | JPA/JSON 경계에서 편함 | 객체 하나 비용 (대부분 무시 가능) |

**원칙: value class는 도메인 내부에서만, 어댑터 경계(JPA 엔티티·DTO)에서는 `.value`로 풀어서 원시값으로 넘긴다.**
막히면 data class로 후퇴해도 되며, 그 판단 근거를 말할 수 있으면 충분하다.

## 5. 어디까지 값 객체로 만드나 — 균형

모든 필드를 값 객체로 만들면 과하다. 기준: **"이 값에 딸린 규칙이나 동작이 있는가?"**

| 값 | 규칙/동작 | 판단 |
|---|---|---|
| Email | 형식 검증, 정규화 | 만들 가치 있음 |
| Money | 음수 불가, 통화, 연산 | 만들 가치 있음 (Phase 3) |
| Password(해시) | 평문과 구분, `==` 비교 금지 | 만들 가치 있음 — 타입으로 "이건 해시다"를 보장 |
| name | 없음 | String으로 충분 |

## 면접 예상 질문

**Q. 값 객체를 왜 쓰나요?**
→ 원시 타입은 개념의 규칙을 담지 못해 검증이 흩어지고 자리 바꿈 실수를 컴파일러가 못 잡는다. 값 객체로 규칙을 한 곳에 모으고, 유효하지 않은 값의 생성 자체를 막는다(always-valid).

**Q. Kotlin의 value class와 data class 차이는?**
→ value class는 컴파일 시 원시값으로 인라이닝되어 런타임 객체가 없다(비용 0, 프로퍼티 1개 제약). data class는 일반 객체. JPA/Jackson 경계에서는 data class 또는 원시값으로 푸는 게 실용적.

## 연결 문서

- [02-validation-layers.md](02-validation-layers.md) — always-valid domain model
- [kotlin-guide/02-domain-modeling.md](../kotlin-guide/02-domain-modeling.md) — value class 문법
