# Testcontainers — 테스트가 자기 DB를 스스로 챙기게 하기

> Phase 3 Step 0에서 PostgreSQL로 전환하자 생긴 질문: "그럼 테스트는 어느 DB로 도나?"
> H2 유지 vs Testcontainers를 비교해 후자를 선택한 기록 + 도입 중 만난 2.0 개명 사건.

## 1. 문제: 통합 테스트의 DB는 어디에 있어야 하나

PostgreSQL 전환 직후 실측한 사실: **compose 컨테이너를 끄면 테스트 스위트 전체가 죽는다** (PSQLException).
테스트가 "내 PC에 미리 떠 있는 개발 DB"에 결박된 것. 선택지 비교:

| 선택지 | 장점 | 치명적 단점 |
|---|---|---|
| **개발 DB 공유** (전환 직후 상태) | 설정 0 | 다른 PC/CI에서 즉사. 롤백 안 하는 테스트가 개발 데이터 오염. 병렬 실행 충돌 |
| **테스트만 H2** | 빠름, Docker 불필요 | V1 마이그레이션이 PostgreSQL 방언(bigserial 등)이라 H2에서 안 돔 → 테스트만 Flyway 끄고 create-drop으로 우회하면 **"테스트 스키마 ≠ 실제 스키마"** 균열 |
| **Testcontainers** | 테스트가 진짜 PostgreSQL + 진짜 마이그레이션으로 돔 | Docker 데몬 필요, 컨테이너 기동 비용(2~5초/1회) |

결정: Testcontainers. 핵심 근거는 **"테스트가 검증하는 스키마 = 운영에 적용될 스키마"**.
H2 우회는 Flyway를 도입한 이유(스키마는 코드다)를 테스트에서만 배신하는 것이다.

## 2. Testcontainers가 뭔가

**테스트 코드가 Docker 컨테이너를 코드로 띄우고, 테스트가 끝나면 버리는 라이브러리.**
DB뿐 아니라 Redis, Kafka, LocalStack 등 뭐든. 컨테이너는 랜덤 포트로 떠서 병렬 실행끼리도 안 겹친다.

우리 구성 (Boot 3.1+ 1급 지원 방식):

```kotlin
// build.gradle.kts — 버전 없음: Boot BOM이 관리
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:testcontainers-postgresql")   // ⚠️ 2.x 이름 주의 (아래 4장)

// 테스트 소스의 설정 클래스
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer =
        PostgreSQLContainer("postgres:16-alpine")   // 운영과 같은 이미지 버전
}

// 각 통합 테스트에
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class AuthFlowApiTest { ... }
```

url/username/password 설정이 **한 줄도 없다**는 게 포인트.

## 3. `@ServiceConnection`의 마법 해체

예전 방식은 컨테이너를 띄운 뒤 접속 정보를 손으로 이어줬다:

```kotlin
// 구식 (@DynamicPropertySource) — 지금은 쓸 필요 없음
@DynamicPropertySource
fun props(registry: DynamicPropertyRegistry) {
    registry.add("spring.datasource.url") { container.jdbcUrl }
    ...
}
```

`@ServiceConnection`은 "이 빈은 PostgreSQL 컨테이너"라는 걸 Boot가 인식해
**datasource 연결 정보를 자동 배선**한다. 결과:

- yml의 `localhost:5432`는 테스트에서 무시되고 컨테이너(랜덤 포트)로 붙음
- Flyway도 같은 datasource → 컨테이너에 V1부터 전부 실행 → 매번 깨끗한 실제 스키마
- **compose = 앱 실행용, Testcontainers = 테스트용**으로 역할 분리 완성

### 컨테이너는 몇 번 뜨나 — 컨텍스트 캐싱과의 만남

컨테이너 빈의 수명 = 스프링 컨텍스트의 수명. 테스트 클래스들이 **같은 애너테이션 조합**(같은 컨텍스트)을
쓰면 전체 테스트 런에 컨테이너 1개만 뜬다 (Phase 2에서 배운 컨텍스트 캐싱이 여기서 효자).
반대로 컨텍스트를 분열시키는 애너테이션(@MockitoBean 등)이 생기면 컨테이너도 하나 더 뜬다 — 느려지면 이걸 의심.

JUnit이 수명을 관리하는 별도 방식(`@Testcontainers`/`@Container`, junit-jupiter 모듈)도 있지만,
스프링 프로젝트에선 `@ServiceConnection` 빈 방식이 표준이고 그 모듈은 필요 없다.

## 4. 사건 기록: Testcontainers 2.0 개명 (Boot 4 개명 시리즈 3탄)

`Unresolved reference 'PostgreSQLContainer'`로 시작된 수사 기록:

```bash
./gradlew :apps:api:dependencies --configuration testRuntimeClasspath | grep FAILED
# → org.testcontainers:postgresql FAILED     ← 범인
```

**BOM 관리 의존성이 FAILED = "코드 문제가 아니라 좌표가 틀렸거나 BOM이 그 이름을 모른다".**
Boot 4.1 BOM은 Testcontainers 2.x만 관리하는데, 2.0에서 이름이 갈렸다:

| | 1.x (검색하면 아직 이게 잔뜩 나옴) | 2.x (현재) |
|---|---|---|
| 아티팩트 | `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` |
| 패키지 | `org.testcontainers.containers.PostgreSQLContainer` | `org.testcontainers.postgresql.PostgreSQLContainer` |
| 타입 | `PostgreSQLContainer<*>` (self-bounded 제네릭) | `PostgreSQLContainer` (제네릭 제거) |

`<*>`가 사라진 이유: 1.x는 체이닝 메서드가 자식 타입을 반환하게 하려는 self-bounded generic
(`PostgreSQLContainer<SELF extends PostgreSQLContainer<SELF>>`) 트릭을 썼는데, 사용자에게 `<*>` 소음만 남겨서 2.0이 폐기했다.

h2console 분리, security-test 분리에 이어 세 번째 교훈:
**Boot 메이저 직후엔 "그 의존성의 현재 이름"부터 의심하라. 블로그 예제는 대부분 옛 이름이다.**

## 5. 결정적 검증 (2026-09-02 실측)

```bash
docker stop my-postgres && ./gradlew :apps:api:test --rerun
```

- 도입 전: **전 테스트 사망** (PSQLException)
- 도입 후: **16개 중 15개 그린**, 유일한 실패 = `@Import`를 빠뜨린 CommerceLabApplicationTests

실패 하나가 오히려 완벽한 증거다 — @Import 있는 테스트는 자기 컨테이너로 살고,
없는 테스트만 localhost를 찾다 죽었다. **@Import는 통합 테스트 전체에 일관 적용**해야 한다는 것까지 실측으로 확인.

## 면접 예상 질문

**Q. 통합 테스트 DB를 어떻게 구성하나요?**
→ Testcontainers로 운영과 같은 PostgreSQL을 테스트별 컨테이너로 띄운다. H2 대체는 방언 차이로
마이그레이션 검증이 안 돼 "테스트 스키마 ≠ 운영 스키마" 위험. 컨테이너는 스프링 컨텍스트당 1회 기동이라 비용 감당 가능.

**Q. @ServiceConnection이 뭔가요?**
→ 컨테이너 빈을 Boot가 인식해 datasource 등 접속 정보를 자동 배선하는 애너테이션 (Boot 3.1+).
@DynamicPropertySource로 손수 잇던 보일러플레이트를 대체.

**Q. Testcontainers의 단점은?**
→ Docker 데몬 의존(CI에도 필요), 컨테이너 기동 비용, 컨텍스트 분열 시 컨테이너 중복 기동.
그래도 "진짜 DB로 검증"의 가치가 커서 업계 표준.

## 연결 문서

- [02-validation-layers.md](02-validation-layers.md) — DB 제약 겹 (진짜 DB여야 검증되는 것들)
- docs/assignments/phase-3-cart-order.md — Step 0 원 과제
- CLAUDE.md — Boot 4 함정 목록
