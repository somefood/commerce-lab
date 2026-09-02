# Phase 3 과제 — 장바구니 & 주문 + PostgreSQL 전환

> 이커머스의 본체에 진입한다. 주문(Order)은 이 도메인에서 가장 복잡한 애그리거트고,
> 여기서 내리는 설계 결정들이 Phase 4(결제)~8(서비스 분리)의 토대가 된다.
> 인프라도 어른이 된다: H2 → PostgreSQL(Docker), ddl-auto → Flyway 마이그레이션.

## 목표

1. `modules/order` 신설 — 장바구니와 주문을 헥사고날로 구현
2. **주문 스냅샷**: 주문 시점의 상품명/가격을 복사 저장
3. **상태 머신**: 허용된 전이만 가능한 주문 상태 관리
4. **Money 값 객체**: `price: Long` 탈출 (Phase 2에서 예고한 것)
5. PostgreSQL + Flyway 전환 — "스키마는 코드다"

## 새 모듈 구조

```
modules/order/
  domain/           Order, OrderItem, OrderStatus, Cart, CartItem, (공용) Money
  application/
    port/in/        PlaceOrderUseCase, CancelOrderUseCase, GetOrderQuery, CartUseCase...
    port/out/       OrderRepository, CartRepository
    service/
  adapter/
    in/web/
    out/persistence/
```

Money는 product/order 양쪽이 쓰므로 `modules/common`으로 갈 수도 있다.
**어디에 둘지는 본인이 결정하고 근거를 말할 것** (힌트: common에 두면 common이 "공유 커널"이 된다 — 트레이드오프가 있다).

## Step 0 — PostgreSQL + Flyway (도메인 손대기 전에 인프라부터)

1. `docker-compose.yml` 작성 (postgres 16, 볼륨, 환경변수)
2. `runtimeOnly("org.postgresql:postgresql")` + datasource 설정 교체
3. Flyway 도입: `V1__init.sql`에 **기존 member/product 테이블**을 직접 SQL로 작성
4. `ddl-auto: create-drop` → `validate` 로 변경 — 이제 스키마의 주인은 Hibernate가 아니라 마이그레이션 파일
5. 테스트는 어떻게? H2를 계속 쓸지, Testcontainers로 갈지 **조사해서 제안할 것** (리뷰 때 논의)

> 순서가 Step 0인 이유: 도메인이 커진 뒤에 DB를 갈면 마이그레이션 빚이 쌓인다. 작을 때 갈아탄다.

## Step 1 — Money 값 객체

- `amount: Long` (원 단위), 음수 금지, `plus`/`times` 연산
- `Product.price`를 Money로 리팩토링 (도메인만! JPA 엔티티/DTO 경계는 Long 유지 — 04 노트의 원칙)
- 곱셈이 왜 필요한지는 Step 3에서 알게 된다 (가격 × 수량)

## Step 2 — 장바구니

| API | 규칙 |
|---|---|
| `POST /api/cart/items` | 로그인 필수. 상품/수량 담기. 이미 담긴 상품이면 수량 합산 |
| `PATCH /api/cart/items/{productId}` | 수량 변경 (1 미만 금지) |
| `DELETE /api/cart/items/{productId}` | 항목 제거 |
| `GET /api/cart` | 내 장바구니 (상품명/가격/합계 포함) |

- 회원당 장바구니 하나. 없으면 첫 담기에 생성
- **재고와 무관하게 담긴다** (재고 검증은 주문 시점 — 왜 그런지 생각해볼 것)
- 존재하지 않는 상품을 담으면? 삭제된(soft delete) 상품이면? — 엣지를 테스트로 못박기

## Step 3 — 주문 생성 (이번 Phase의 심장)

`POST /api/orders` : 내 장바구니 전체를 주문으로 전환

1. 장바구니가 비어 있으면 400
2. **각 항목의 재고 확인 → 차감** (하나라도 부족하면 주문 전체 실패 — 트랜잭션 경계를 생각하라)
3. Order + OrderItem 생성. OrderItem에는 **주문 시점의 상품명/가격(Money)을 복사** —
   상품 테이블 참조가 아니라 값 자체를 저장한다
4. 장바구니 비우기
5. 응답: 주문 id, 항목 목록, 총액, 상태(`ORDERED`)

애그리거트 규칙:
- Order와 OrderItem은 **한 덩어리** — OrderItem은 Order를 통해서만 생성/접근 (GLOSSARY의 애그리거트 항목)
- 총액은 저장할까 계산할까? — 결정하고 근거를 말할 것

## Step 4 — 주문 상태 머신 + 취소

```
ORDERED ──> CANCELED        (Phase 3에서는 이 전이만)
   └──────> (PAID, SHIPPED... 는 Phase 4+에서 추가)
```

- `POST /api/orders/{id}/cancel` : **본인 주문만**, ORDERED 상태에서만 가능
- 취소 시 **재고 복원**
- 전이 규칙은 도메인 메서드로 (`order.cancel()`), 불가능한 전이는 예외 —
  상태를 setter로 바꾸는 코드가 어댑터/서비스에 존재하면 안 된다
- 남의 주문 취소 시도 → 무슨 상태코드? 403? 404? **결정하고 근거를** (힌트: Q1에서 배운 정보 노출 관점)

## Step 5 — 주문 조회

- `GET /api/orders` : 내 주문 목록 (최신순)
- `GET /api/orders/{id}` : 상세 (본인 것만)

## 설계 숙제 (구현 전에 생각해올 것)

1. **공통 예외 체계**: 도메인 예외가 늘고 있다 (DuplicateEmail, InvalidAuthentication, 이제 재고부족/전이불가/…).
   RuntimeException 직접 상속을 계속할지, 공통 부모(에러코드 포함)를 둘지 — 제안을 가져올 것
2. **모듈 간 참조**: order가 상품 정보(이름/가격/재고)를 얻는 방법 —
   product의 서비스를 직접 호출? 포트를 통해? Phase 8 분리를 생각하면 어느 쪽이 유리한가

## 수용 기준

- [ ] `docker compose up` + `./gradlew build` 그린 (Flyway 마이그레이션 적용 확인)
- [ ] 장바구니 CRUD + 인증 (남의 장바구니 접근 불가)
- [ ] 주문 생성: 스냅샷 저장, 재고 차감, 재고 부족 시 전체 롤백을 테스트로 증명
- [ ] 상품 가격을 주문 후 변경해도 기존 주문의 금액이 불변임을 테스트로 증명
- [ ] 취소: 상태 전이 규칙 + 재고 복원 + 본인 확인
- [ ] 불가능한 상태 전이가 예외임을 도메인 단위 테스트로 증명
- [ ] Money 연산 단위 테스트

## 리뷰 때 물어볼 질문 (미리 생각해오기)

1. 주문에 상품명/가격을 복사하는 건 정규화 위반이다. 그런데 왜 이커머스에서는 이게 정답인가?
2. 재고 차감을 "주문 시"에 하기로 했다. "결제 시" 차감과 비교해 각각 무엇이 좋고 나쁜가?
   (드롭잇처럼 선착순 판매라면 어느 쪽이 맞을까?)
3. 장바구니를 DB 테이블에 두었다. Redis나 클라이언트(localStorage)에 두는 선택지와 비교하면?

## 참고

- GLOSSARY: 애그리거트, 주문 스냅샷, 주문 상태 머신, 재고 차감, 오버셀링
- learning/01 (Lost Update — 재고 차감에서 재회한다), 04 (값 객체), 05 (생성/복원 팩토리 — Order에도 적용)
- 동시 주문 경쟁(오버셀링)의 본격 해결은 Phase 5(락). 이번엔 단일 요청 정합성까지만.
