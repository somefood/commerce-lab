# 과제: Phase 0 온보딩 + Phase 1 상품 CRUD

> 팀장(Claude)이 내는 첫 과제. 완료하면 코드 리뷰를 요청할 것.
> 막히면 언제든 질문 — 단, "어떻게 시도해봤는지"를 같이 말해주면 더 좋은 피드백을 줄 수 있다.

## Phase 0 — 온보딩 체크리스트

- [x] IntelliJ로 프로젝트 열기 (루트 폴더 열면 Gradle 자동 인식)
- [x] `./gradlew build` 성공 확인
- [x] `./gradlew :apps:api:bootRun` 실행 후 http://localhost:8080/actuator/health 에서 `{"status":"UP"}` 확인
- [x] `docs/ARCHITECTURE.md` 정독 — 특히 "레이어드와의 결정적 차이" 부분
- [x] `docs/kotlin-guide/01-essentials.md` 한 번 훑기 (외우지 말 것)
- [x] 첫 커밋: `git add -A && git commit -m "chore: 프로젝트 초기 구조"`

## Phase 1 — 상품 CRUD (헥사고날로)

### 요구사항 (드롭잇 서비스 기준)

브랜드 관리자가 판매할 상품을 등록/관리한다.

1. **상품 등록** — 이름(필수, 최대 100자), 가격(필수, 0 이상), 설명(선택), 초기 재고 수량(필수, 0 이상)
2. **상품 단건 조회** — 없는 ID면 404
3. **상품 목록 조회** — 일단 전체 목록 (페이징은 리뷰 후 2차로)
4. **상품 수정** — 이름/가격/설명 변경 가능
5. **상품 삭제** — 실제 삭제 대신 **판매중지(soft delete)** 로 구현할 것
   (이유를 생각해볼 것 — 힌트: 이미 이 상품을 주문한 사람이 있다면?)

### API 스펙 (팀장 설계)

| Method | Path | 성공 | 실패 |
|---|---|---|---|
| POST | `/api/products` | 201 + 생성된 상품 | 400 (검증 실패) |
| GET | `/api/products/{id}` | 200 | 404 |
| GET | `/api/products` | 200 (배열) | - |
| PUT | `/api/products/{id}` | 200 | 400, 404 |
| DELETE | `/api/products/{id}` | 204 | 404 |

### 구현 순서 (안쪽 → 바깥쪽 — 헥사고날의 정석 순서)

레이어드에 익숙하면 Controller부터 만들고 싶겠지만, 반대로 간다:

1. **`domain/Product.kt`** — 순수 Kotlin data class. 스프링/JPA import 금지.
   상태(판매중/판매중지)도 도메인이 가진다.
2. **`application/port/in/`** — 유스케이스 인터페이스.
   예: `RegisterProductUseCase`, `GetProductQuery` (등록용 커맨드 객체도 여기에)
3. **`application/port/out/ProductRepository.kt`** — **인터페이스**.
   "저장해줘, 찾아줘"라는 요구만 선언. JPA라는 단어가 등장하면 안 된다.
4. **`application/service/ProductService.kt`** — port/in 구현, port/out 사용. `@Service`
5. **`adapter/out/persistence/`** — `ProductJpaEntity`(JPA 엔티티), `ProductJpaRepository`(Spring Data),
   그리고 port/out 인터페이스를 구현하는 `ProductPersistenceAdapter`.
   **도메인 Product ↔ JPA 엔티티 매핑 함수를 여기서 작성** (이 분리가 헥사고날의 핵심 경험)
6. **`adapter/in/web/ProductController.kt`** — 요청/응답 DTO 정의, port/in 호출

### 수용 기준 (이걸 만족하면 리뷰 요청)

- [ ] 위 5개 API가 스펙대로 동작 (curl 또는 IntelliJ HTTP Client로 확인)
- [ ] `domain/` 패키지에 스프링/JPA import가 하나도 없다
- [ ] `ProductService`는 JPA 클래스를 모른다 (port/out 인터페이스만 사용)
- [ ] 검증 실패 시 400과 함께 무엇이 잘못됐는지 메시지가 내려간다
- [ ] 테스트 최소 2개:
  - `ProductService` 단위 테스트 — **DB 없이** (port/out을 페이크 구현으로 대체)
  - API 통합 테스트 1개 (`@SpringBootTest` + MockMvc, 등록→조회 시나리오)
- [ ] 커밋을 의미 단위로 쪼갤 것 (예: 도메인 → 애플리케이션 → 어댑터)

### 시작을 위한 최소 힌트

도메인 모델의 뼈대 (이 정도만 주고 나머지는 직접):

```kotlin
package com.commercelab.product.domain

data class Product(
    val id: Long?,          // 저장 전에는 null — "왜 Long이 아니라 Long?인가"를 생각해볼 것
    val name: String,
    val price: Long,
    val description: String?,
    val stockQuantity: Int,
    val status: ProductStatus,
) {
    fun deactivate(): Product = copy(status = ProductStatus.INACTIVE)  // 불변 객체 스타일
}

enum class ProductStatus { ACTIVE, INACTIVE }
```

port/out의 모양 (JPA가 안 보이는 것에 주목):

```kotlin
package com.commercelab.product.application.port.out

import com.commercelab.product.domain.Product

interface ProductRepository {
    fun save(product: Product): Product
    fun findById(id: Long): Product?
    fun findAll(): List<Product>
}
```

### 자주 빠지는 함정 (미리 경고)

1. JPA 엔티티를 domain 패키지에 두고 싶어진다 → 참아라. 매핑이 귀찮은 게 정상이고, 그 귀찮음의 대가가 뭔지 리뷰에서 이야기하자
2. `!!` 남발 → 대부분 `?:`로 해결된다
3. Controller에 비즈니스 로직 스며들기 → Controller는 "번역"(HTTP ↔ 유스케이스)만
4. Kotlin에서 Lombok 찾기 → 필요 없다, data class가 그 역할

완료하면 "리뷰해줘"라고 말해주면 된다. 실무 PR 리뷰처럼 피드백하겠다.
