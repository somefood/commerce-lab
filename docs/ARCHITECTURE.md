# 아키텍처 가이드

3년차 눈높이로 설명하는 이 프로젝트의 구조. 레이어드 아키텍처 경험이 있다는 전제로,
**"레이어드와 뭐가 다른가"**를 중심으로 설명한다.

## 1. 멀티모듈 — 코드를 물리적으로 나눈다

레이어드 프로젝트에서는 보통 패키지로만 나눈다 (`controller/`, `service/`, `repository/`).
문제는 **패키지는 강제력이 없다**는 것. `OrderService`가 `ProductRepository`를 직접 import해도
컴파일러는 아무 말도 안 한다. 3년 뒤엔 모든 것이 모든 것을 참조하는 진흙탕(Big Ball of Mud)이 된다.

멀티모듈은 이 경계를 **Gradle 모듈 = 컴파일 단위**로 강제한다:

```
apps/api ──────> modules/product ──> modules/common
   │
   └─────(추후)─> modules/order ───> modules/common
```

- `modules/order`의 build.gradle.kts에 `implementation(project(":modules:product"))`를
  선언하지 않으면, order 코드에서 product 클래스를 import하는 순간 **컴파일 에러**.
- 의존을 추가하려면 build 파일을 고쳐야 하고, 그건 코드 리뷰에서 눈에 띈다.
  → "경계를 넘는 결정"이 암묵적이 아니라 명시적이 된다.

### 모듈의 역할 구분

| 모듈 | 성격 | 규칙 |
|---|---|---|
| `apps/api` | 실행 가능한 앱 (bootstrap) | 조립만 한다. 비즈니스 로직 금지 |
| `modules/{도메인}` | 도메인 라이브러리 | 다른 도메인 모듈에 직접 의존하지 않는 것이 원칙 (필요 시 이벤트로 통신 — Phase 6) |
| `modules/common` | 공유 커널 | 정말 공통인 것만. 쓰레기장 되지 않게 엄격 관리 |

## 2. 헥사고날 아키텍처 — 도메인을 가운데 둔다

레이어드의 의존 방향은 `Controller → Service → Repository → DB`. 문제는 **비즈니스 로직(Service)이
기술(Repository/JPA)에 의존**한다는 것. DB를 바꾸거나 외부 API가 바뀌면 비즈니스 로직이 흔들린다.

헥사고날(= 포트와 어댑터)은 방향을 뒤집는다: **모든 의존은 도메인을 향한다.**

```
        [들어오는 어댑터]                        [나가는 어댑터]
   REST Controller, 스케줄러 ─┐          ┌─ JPA Repository, 외부 API 클라이언트
                              ▼          ▼
                    ┌──────────────────────────┐
                    │       application        │
                    │  port/in   ←  유스케이스  │
                    │  service   =  구현체      │
                    │  port/out  →  요구사항    │
                    └────────────┬─────────────┘
                                 ▼
                    ┌──────────────────────────┐
                    │         domain           │
                    │  순수 Kotlin. 스프링/JPA  │
                    │  import가 하나도 없다     │
                    └──────────────────────────┘
```

product 모듈 안에서의 실제 배치:

```
com.commercelab.product
├── domain/                      # Product 도메인 모델 (순수 Kotlin)
├── application/
│   ├── port/in/                 # 예: RegisterProductUseCase (인터페이스)
│   ├── port/out/                # 예: ProductRepository (인터페이스!)
│   └── service/                 # 예: ProductService (유스케이스 구현)
└── adapter/
    ├── in/web/                  # ProductController — port/in을 호출
    └── out/persistence/         # ProductJpaEntity, port/out 구현체
```

### 레이어드와의 결정적 차이 한 가지

레이어드: `Service`가 `JpaRepository`를 직접 주입받는다. (비즈니스가 기술에 의존)

헥사고날: `Service`는 자신이 정의한 `port/out` 인터페이스만 안다.
JPA 어댑터가 그 인터페이스를 **구현**한다. (기술이 비즈니스에 의존 — 의존성 역전, DIP)

효과:
- 도메인/서비스 테스트에 DB가 필요 없다 (port를 페이크로 갈아끼움)
- "JPA 엔티티"와 "도메인 모델"이 분리되어, DB 스키마 사정이 비즈니스 코드에 새어들지 않는다
- 어댑터는 갈아끼우는 부품이 된다 (H2 → PostgreSQL 전환이 어댑터 교체로 끝남)

비용도 있다: 클래스 수가 늘고 매핑 코드가 생긴다. **이 트레이드오프를 몸으로 느끼는 것**이
이 프로젝트의 학습 목표 중 하나다.

## 3. 설계 결정 기록 (ADR)

> 면접 팁: "무엇을 썼다"보다 "왜 그걸 선택했고 무엇을 포기했다"가 강하다.
> 결정할 때마다 여기에 기록을 쌓는다.

### ADR-001: 모듈러 모놀리스로 시작한다 (2026-08-26)

- **상황**: 1인 개발, 미니 PC 1대 배포, 학습 + 포트폴리오 목적
- **결정**: MSA가 아닌 모듈러 모놀리스. 단, 모듈 경계를 서비스 경계처럼 설계하고
  Phase 8에서 결제 모듈을 실제로 분리한다.
- **근거**: MSA를 처음부터 하면 도메인/아키텍처 학습 시간이 인프라 운영에 잠식된다.
  모듈 경계 설계 능력이 서비스 분리 능력의 전제 조건이다.
- **포기한 것**: 처음부터 서비스별 독립 배포/스케일링 경험

### ADR-002: 버전 카탈로그 + 모듈별 build.gradle.kts (2026-08-26)

- **결정**: 버전은 `gradle/libs.versions.toml`에서 중앙 관리, 각 모듈은 자기 빌드 파일에
  플러그인/의존성을 명시적으로 선언
- **근거**: 루트에서 `subprojects {}` 블록으로 일괄 주입하는 방식보다, 각 모듈이 무엇을
  쓰는지 그 모듈 파일만 봐도 알 수 있는 쪽이 학습과 유지보수에 낫다

### ADR-003: Spring Boot 4.1 사용 (2026-08-26)

- **결정**: 현재 최신 정식 버전인 Boot 4.1 채택
- **근거**: 포트폴리오 신선도. Boot 3 → 4 차이(모듈화된 스타터, Jackson 3 등)를 아는 것 자체가 어필 포인트
- **리스크**: 국내 블로그 자료 대부분이 Boot 2~3 기준이라 검색 결과와 다를 수 있음 → 공식 문서 우선
