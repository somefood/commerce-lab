# Kotlin 가이드 1편 — Phase 1에서 바로 쓰는 필수 문법

Java를 안다는 전제로, "Java였으면 이렇게 썼을 것"과 비교하며 설명한다.
전부 외울 필요 없다. 과제하다 막히면 돌아와서 찾아보는 용도.

## 1. val / var — 변수 선언

```kotlin
val name = "드롭잇"   // val = 재할당 불가 (Java의 final). 기본으로 이걸 써라
var count = 0          // var = 재할당 가능. 꼭 필요할 때만
```

타입은 추론되지만 명시할 수도 있다: `val price: Long = 10_000`
(Kotlin은 숫자에 `_` 구분자 허용 — 금액 다룰 때 가독성 좋음)

## 2. Null 안전성 — Kotlin의 최대 무기

타입 자체가 null 가능 여부를 구분한다. NPE를 컴파일 타임에 잡는다.

```kotlin
val a: String = "hello"    // null 절대 불가
val b: String? = null      // ? 붙으면 null 가능

b.length      // ❌ 컴파일 에러! null일 수 있으니 바로 접근 금지
b?.length     // ✅ safe call: b가 null이면 전체가 null (Java의 if(b != null) b.length())
b?.length ?: 0 // ✅ 엘비스 연산자: null이면 기본값 0
b!!.length    // ⚠️ "null 아님을 내가 보증" — 틀리면 NPE. 코드 리뷰에서 지적 대상
```

실전 패턴 — 조회 결과가 없으면 예외:

```kotlin
val product = repository.findById(id)
    ?: throw NoSuchElementException("상품이 없습니다: $id")
```

## 3. data class — DTO/도메인 모델의 표준

```kotlin
data class Product(
    val id: Long,
    val name: String,
    val price: Long,
)
```

이 4줄이 Java의 생성자 + getter + equals + hashCode + toString + 빌더 대부분을 대체한다.

```kotlin
val p = Product(id = 1, name = "티셔츠", price = 29_000)  // named argument — 순서 무관, 가독성 ↑
val discounted = p.copy(price = 19_000)  // copy: 일부만 바꾼 새 객체 (불변 유지의 핵심)
```

## 4. 함수 — 기본값, 표현식 본문

```kotlin
// 블록 본문
fun add(a: Int, b: Int): Int {
    return a + b
}

// 표현식 본문 — 한 줄이면 이렇게 (return, 반환 타입 생략 가능)
fun add(a: Int, b: Int) = a + b

// 기본값 파라미터 — Java의 오버로딩 지옥이 사라진다
fun search(keyword: String, page: Int = 0, size: Int = 20) = ...
search("티셔츠")             // page=0, size=20
search("티셔츠", size = 50)  // named argument로 골라서 지정
```

## 5. 문자열 템플릿

```kotlin
val msg = "상품 $name 의 가격은 ${product.price}원"  // Java의 String.format 대체
```

## 6. if / when — 문(statement)이 아니라 식(expression)

```kotlin
val grade = if (amount >= 100_000) "VIP" else "일반"  // if가 값을 반환한다

val label = when (status) {          // switch보다 훨씬 강력
    OrderStatus.PAID -> "결제완료"
    OrderStatus.SHIPPED -> "배송중"
    else -> "기타"
}
// when은 enum을 전부 커버하면 else 생략 가능 → 새 상태 추가 시 컴파일 에러로 누락을 잡아줌
```

## 7. 컬렉션 — 스트림보다 간결

```kotlin
val names = products.map { it.name }               // it = 람다의 단일 파라미터 암묵 이름
val cheap = products.filter { it.price < 10_000 }
val total = products.sumOf { it.price }
val byId = products.associateBy { it.id }          // Map<Long, Product> 만들기
val first = products.firstOrNull { it.name == "티셔츠" }  // 없으면 null (없으면 예외인 first()와 구분)
```

Java 스트림과 달리 `.stream()...collect()` 없이 바로 체이닝. `listOf()`, `mutableListOf()`로 생성.

## 8. 스코프 함수 — 처음엔 2개만 (let, apply)

```kotlin
// let: null 아닐 때만 블록 실행 + 변환
val length = name?.let { it.length }

// apply: 객체 설정 후 자기 자신 반환 (빌더처럼)
val entity = ProductJpaEntity().apply {
    name = "티셔츠"
    price = 29_000
}
```

`run`, `also`, `with`도 있지만 남용하면 오히려 가독성이 떨어진다. 필요해질 때 소개하겠다.

## 9. 클래스는 기본 final — 스프링과의 관계

Kotlin 클래스는 `open`을 붙이지 않으면 상속 불가(final)다. 스프링 AOP/JPA 프록시는 상속이
필요해서 충돌하는데, 우리 빌드에 적용된 `kotlin("plugin.spring")` / `kotlin("plugin.jpa")`가
필요한 클래스를 컴파일 시점에 자동으로 열어준다. (build.gradle.kts 주석 참고)

## 10. 생성자 주입 — 스프링 실전 형태

```kotlin
@Service
class ProductService(
    private val productRepository: ProductRepository,  // 주 생성자에서 바로 프로퍼티 선언
) : RegisterProductUseCase {                           // 인터페이스 구현은 콜론(:)
    ...
}
```

`@Autowired` 불필요 — 생성자가 하나면 스프링이 알아서 주입한다. Lombok 없이 이게 기본.

---

**다음 편 예고 (Phase 2~3에서)**: sealed class로 상태 모델링, companion object와 팩토리 함수,
확장 함수, 제네릭. 지금은 몰라도 된다.
