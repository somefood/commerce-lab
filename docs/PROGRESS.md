# 진도

> **문서 지도** — 이 파일이 시작점이다.
> [설계문서](./superpowers/specs/2026-08-18-commerce-lab-design.md) · [협업 규칙](../CLAUDE.md) ·
> [마일스톤](./milestones/) · [ADR](./adr/) · [인프라](../infra/README.md) · [README](../README.md)

세션 시작 시 Claude가 가장 먼저 읽는 파일이다. 현재 위치와 다음 할 일을 여기서 판단한다.

## 현재 마일스톤

**M1 — 주문 코어와 동시성 제어** — 1단계(락 없이). 도메인 완료, 영속성·REST 어댑터 차례. 브랜치 `m1-step1-no-lock`

작업지시서: [M1-order-core.md](./milestones/M1-order-core.md) — 할 일 목록은 그 문서 §13.

---

## 인수인계 (2026-08-20 밤 기준)

다른 머신에서 이어받을 때 이 절만 읽으면 된다. 브랜치는 `m1-step1-no-lock`.

### 지금 딱 멈춘 지점

M1 §13-4(영속성 + REST 어댑터)의 **골격만 만들어 둔 상태**다. 각 계층의 자리는 잡혔고
구현 본문이 비어 있다(`TODO()`).

```bash
cd backend
./gradlew :modules:order:order-core:test   # 16건 통과
./gradlew :bootstrap:archTest              # 7건 통과
./gradlew :bootstrap:test                  # ConcurrentOrderIntegrationTest 1건 실패 ← 정상
```

통합 테스트가 실패하는 이유는 엔드포인트가 비어 있어서다. 아직 "오버셀 관측"이 아니다.

### 사용자가 할 일 (다음 세션의 첫 작업)

**1. 즉시 고칠 것**
- `DevController`의 경로가 `/api/dev/rest`다. 통합 테스트와 k6는 `/api/dev/reset`을 호출한다
- `OrderPlacementService`가 `jakarta.transaction.Transactional`을 쓴다.
  스프링 것(`org.springframework.transaction.annotation.Transactional`)으로 바꾼다.
  ArchUnit 규칙이 두 애노테이션을 모두 검사하도록 확장해 뒀다

**2. 포트 메서드 정의** (`domain/OrderRepository.kt`, `domain/InventoryRepository.kt` — 지금 비어 있음)

주문 생성 흐름을 문장으로 적으면 메서드 목록이 나온다.
> 상품이 존재하고 활성 상태인지 확인 → 재고를 읽는다 → 남은 수량 검사 → 선점 수량을 늘린다 → 주문을 저장한다

여기서 1단계의 핵심 선택이 있다. **"재고를 읽는다"와 "선점 수량을 늘린다"를 별도 메서드로 둔다.**
그 사이가 벌어지는 것이 오버셀이 재현되는 통로이고, 1단계는 그걸 관측하는 것이 목적이다.

**3. 어댑터** — 도메인 ↔ 엔티티 변환, `OrderRepository` 구현 (`infrastructure`)

**4. `OrderPlacementService.place()` 구현** — 락을 걸지 않는다. `@Transactional`은 여기에만

**5. 컨트롤러 본문**
- 반환 타입이 `ResponseEntity<*>`인데, springdoc이 이 타입에서 스키마를 만들 수 없다.
  프론트가 OpenAPI로 타입을 생성하므로 구체 타입으로 바꿔야 계약 검증이 살아난다
- `DomainResult`를 HTTP로 바꾸는 지점에서 `fold`를 쓴다. **재고 부족은 4xx다** (5xx면 통합 테스트가 잡는다)
- `ProblemDetailAdvice`가 잡을 대상은 도메인 실패가 아니라 예외다 (잘못된 JSON, 없는 경로, 예상 못한 런타임 예외)
- `OrderWebDto.kt`는 빈 클래스만 있다. api 타입을 그대로 쓸 거면 삭제

**6. 오버셀 관측** — 통합 테스트 실행 → 실패 확인 → k6 burst → M1 §8 표 1행 기록 → **리뷰 요청**

### Claude가 할 일

- 위 작업 리뷰
- `domain`/`application`에 클래스가 채워지면 ArchUnit 규칙의 `allowEmptyShould(true)` 제거
- 오버셀 수치 해석, 2단계 진입 판단
- 프론트엔드는 2단계 통과 후

### 이번 세션에서 끝난 것

| 항목 | 내용 |
|---|---|
| 도메인 모델 | `Order`/`OrderLine` 완성. 테스트 16건 통과 |
| 패키지 분리 | `domain` / `application` / `infrastructure` (M1 §4-1 결정) |
| ArchUnit | 도메인의 영속성 의존 금지, 트랜잭션 경계 제한. jakarta·spring 양쪽 애노테이션 검사 |
| JPA 배선 | `@EntityScan` / `@EnableJpaRepositories`. 이게 없으면 엔티티가 0개로 스캔된다 |
| 스키마 검증 | `ddl-auto: validate`. 매핑이 어긋나면 부팅에서 실패 |
| 엔티티 | `@Table`로 스키마·테이블 명시, 생성자 프로퍼티로 non-null 유지 |

### 확정된 설계 결정

- **마이그레이션**: Flyway. 이력 테이블은 `public` 스키마 (모듈 스키마에 두면 M4 분리 때 딸려 간다)
- **선점 만료**: 주기 배치 단독. TTL 3분 / 주기 5초 / 1회 500건. Redis는 M1에서 안 쓴다
- **`orders.id`**: `varchar(100)`, 애플리케이션이 생성
- **도메인 실패**: `kotlin.Result` 대신 `common`의 `DomainResult<E, T>`
- **시간**: 애플리케이션이 소유. 도메인 함수가 `now: Instant`를 받고 `updatedAt`을 갱신한다
- **도메인 ≠ 엔티티**: 매핑 비용을 내고 분리한다. ArchUnit이 강제
- **취소 가능 상태**: CREATED, PAID만. SHIPPED/DELIVERED는 거부 (반품은 별도 프로세스)
- ADR-0002 / 0004 / 0005 / 0006 작성 대기 — **사용자 몫**

### 환경 메모

- Gradle 9.3.0 / Kotlin 2.2.21 / Java 21 (`JAVA_HOME=~/.sdkman/candidates/java/current`)
- 인프라: `docker compose -f infra/docker-compose.yml up -d`
- 마이그레이션을 고쳤으면 `down -v` 후 재기동. `public.flyway_schema_history`가 남으면 체크섬이 어긋난다
- DB에 psql로 직접 DDL을 넣지 말 것. Flyway 이력과 어긋나 `relation already exists`가 난다
- 남은 부채: CI의 `actions/checkout@v4`·`setup-node@v4`가 Node 20 타깃이라 경고

---

## 마일스톤 체크리스트

### M0 — 스캐폴딩 (Claude)
- [x] Gradle 멀티모듈 뼈대
- [x] ArchUnit 아키텍처 규칙
- [x] Docker Compose 인프라
- [x] Boot 앱 + Testcontainers 통합 테스트
- [x] 프론트엔드 뼈대 + OpenAPI 타입 생성
- [x] CI + 문서

### M1 — 주문 코어와 동시성 제어 (사용자 구현) — [작업지시서](./milestones/M1-order-core.md)
- [x] 설계문서 작성 (Claude) — [M1-order-core.md](./milestones/M1-order-core.md)
- [ ] 1단계: 락 없이 구현 → 오버셀 관측 — DDL·시그니처·도메인 완료. 영속성·REST 어댑터 차례
- [ ] 2단계: 낙관적 락 + 재시도 정책
- [ ] 3단계: 선점(HELD) + TTL, 만료/확정 경쟁 조건 처리
- [ ] 프론트: 상품 목록 / 주문 / 실시간 재고 (Claude)
- [ ] ADR 2건 이상 (사용자) — ADR-0002 마이그레이션 도구, ADR-0004 만료 구동 방식
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
