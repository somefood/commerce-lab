# 검증은 어디에 두는가 — 3겹 방어선 (Defense in Depth)

> Phase 1에서 나온 질문: "웹 DTO에 Validation을 걸었는데, 도메인/엔티티는 자유롭게 두나?"
> 답: 정반대. 겹마다 지키는 것이 다른 **의도된 중복**이다.

## 한눈에 보기

```
요청 → [1겹: 웹 DTO]    "형식이 말이 되는가"      @field:Size, @Valid     → 400 + 친절한 메시지
     → [2겹: 도메인]     "존재 가능한 상태인가"     init { require(...) }   → 어떤 경로로도 불변식 보장
     → [3겹: DB]        "버그가 나도 데이터는 무사"  NOT NULL, length, unique → 최후의 보루
```

## 1겹 — 웹 DTO: 사용자를 돕는 검증

```kotlin
data class ProductCreateRequest(
    @field:NotBlank
    @field:Size(max = 100, message = "상품명은 100자 이하여야 합니다")
    val name: String,
    ...
)
// 컨트롤러: fun createProduct(@Valid @RequestBody request: ProductCreateRequest)
```

- 검증 대상: 입력의 **형태** — null, 길이, 포맷, 범위
- 목적: 쓰레기 요청을 가장 바깥에서 빨리 거르고, **사용자가 스스로 고칠 수 있는 메시지**를 준다
- 실패 시: `MethodArgumentNotValidException` → `@RestControllerAdvice`가 400으로 번역
- 성격: API 계약의 일부. "이 API는 이런 모양의 요청만 받는다"는 문서 역할

### Kotlin 함정: 왜 `@Size`가 아니라 `@field:Size`인가

Kotlin 주 생성자의 `val name: String`은 한 줄이지만 실제로는 **생성자 파라미터 + 필드 + getter** 세 가지가 만들어진다. 애노테이션을 그냥 붙이면 기본적으로 **파라미터**에 붙는데, Bean Validation은 **필드**에서 읽는다. `@field:`는 "필드에 붙여라"라는 use-site target 지정. 이걸 빼먹으면 검증이 **조용히 무시된다** (에러도 안 남 — 그래서 101자 테스트가 필요했던 것).

## 2겹 — 도메인: 시스템을 지키는 검증 (불변식)

```kotlin
data class Product(...) {
    init {
        require(price >= 0) { "가격은 0 이상이어야 합니다. price=$price" }
        require(stockQuantity >= 0) { "재고 수량은 0 이상이어야 합니다. ..." }
    }

    fun adjustStockQuantity(quantity: Int): Product {
        if (stockQuantity + quantity < 0) throw IllegalArgumentException("...")
        return copy(stockQuantity = stockQuantity + quantity)
    }
}
```

- 검증 대상: **불변식(invariant)** — 비즈니스 규칙상 절대 깨지면 안 되는 조건
- 왜 DTO 검증만으론 부족한가: 도메인 객체는 웹으로만 만들어지지 않는다. **배치, 이벤트 핸들러, 다른 유스케이스, 테스트** — 그 모든 경로에서 웹 DTO의 `@Size`는 무력하다. 도메인 검증만이 "어떤 문으로 들어와도" 지켜진다
- 핵심 개념: **always-valid domain model** — "생성에 성공한 도메인 객체는 항상 유효하다." 유효하지 않은 Product는 아예 존재할 수 없으므로, 서비스 코드 곳곳의 `if (product.price < 0)` 같은 방어 코드가 사라진다
- `require(조건) { 메시지 }`: 조건이 거짓이면 `IllegalArgumentException`을 던지는 Kotlin 표준 함수. 도메인 불변식의 관용구

## 3겹 — 엔티티/DB: 미래의 나를 지키는 검증

```kotlin
@Entity
class ProductJpaEntity(
    @Column(nullable = false, length = 100)
    val name: String,
    ...
)
-- DB: name VARCHAR(100) NOT NULL
```

- 검증 대상: 데이터 무결성 — NOT NULL, 길이, 유니크 제약, 외래키
- 왜 필요한가: 1·2겹은 코드이고 코드엔 버그가 있다. 검증을 우회하는 버그가 생겨도 DB가 막으면 **데이터는 안 썩는다.** 잘못된 데이터는 잘못된 코드보다 수명이 길고, 고치는 비용도 훨씬 크다
- 현재 우리 프로젝트: 아직 미적용 (`ddl-auto: create-drop` 학습 모드). Phase 3에서 PostgreSQL + Flyway로 전환하며 스키마를 직접 관리할 때 적용한다

## 어떤 규칙을 어느 겹에 두는가

기준: **"이게 깨지면 비즈니스가 깨지는가?"**

| 규칙 | DTO | 도메인 | DB | 판단 근거 |
|---|:-:|:-:|:-:|---|
| 가격 ≥ 0 | ✅ | ✅ | (✅) | 깨지면 비즈니스 사고 → 전 겹 방어 |
| 재고 ≥ 0 | | ✅ | | 계산 결과라 DTO에선 검증 불가. 도메인이 주인 |
| 이름 ≤ 100자 | ✅ | 선택 | ✅ | UI/저장소 제약 성격. 도메인 불변식까진 애매 |
| 이름 필수 | ✅ | ✅ | ✅ | 이름 없는 상품은 존재가 무의미 |
| 이메일 포맷 | ✅ | 선택 | | 형식 검증의 전형 (Phase 2에서 만남) |

감각 정리:
- **DTO 검증 = 사용자를 돕는다** (뭘 잘못 보냈는지 빨리, 친절하게)
- **도메인 검증 = 시스템을 지킨다** (어떤 경로로도 불변식 유지)
- **DB 제약 = 미래의 나를 지킨다** (버그가 있어도 데이터는 무사)

목적이 다르므로 중복이 아니라 **역할 분담**이다.

## 덤: 검증 실패 테스트에서 배운 것 (Phase 1 실전 교훈)

같은 400이라도 **이유가 다를 수 있다**:

```
{"amount":-11}   → 400 (역직렬화 실패 — quantity 필드 누락, 스프링 기본 에러 바디)
{"quantity":-11} → 400 (도메인 가드 작동 — 우리가 만든 ErrorResponse)
```

상태코드만 검증하는 테스트는 이 둘을 구분하지 못해 **엉뚱한 이유로 통과**할 수 있다.
→ 실패 케이스 테스트는 반드시 `jsonPath("$.message")`까지 검증해서 "왜 실패했는지"를 못박을 것.

## 면접 예상 질문

**Q. 검증 로직이 계층마다 중복되는데 어떻게 관리하나요?**
→ 중복이 아니라 역할이 다르다: DTO는 요청 형식과 사용자 피드백, 도메인은 모든 진입 경로에서의 불변식, DB는 버그에도 무너지지 않는 무결성. 규칙별로 "깨지면 비즈니스가 깨지는가"를 기준으로 배치한다.

**Q. 도메인 모델에서 setter를 열어두지 않는 이유는?**
→ always-valid를 지키기 위해. 상태 변경을 `adjustStockQuantity()` 같은 의도가 드러나는 메서드로만 허용하면, 검증을 우회한 상태 변경이 불가능해진다.

## 연결 문서

- [01-lost-update.md](01-lost-update.md) — "검증"과 별개로 "동시성"이 데이터를 깨뜨리는 경우
- Phase 2에서 이메일/비밀번호 검증으로 이 프레임을 다시 적용한다
