# 과제: Phase 2 — 회원 & 인증 (JWT)

> Phase 1 졸업 축하. 이번엔 **새 모듈을 밑바닥부터 직접** 세운다. 그리고 Spring Security가 등장한다 —
> 처음 만나면 "왜 내 API가 갑자기 다 401이지?"부터 시작하는 게 정상이다. 그 혼란까지 학습 과정이다.

## 목표

1. `modules/member` 모듈을 **혼자** 세팅 (Gradle 등록부터)
2. 회원가입 / 로그인 → JWT 발급 / 토큰으로 보호된 API 호출
3. 상품 등록·수정·삭제·재고 변경은 **관리자만**, 조회는 누구나

## 요구사항 (드롭잇 서비스 기준)

### 회원
- **회원가입** — 이메일(필수, 이메일 형식, 유니크), 비밀번호(필수, 8자 이상), 이름(필수)
- 비밀번호는 **절대 평문 저장 금지** — BCrypt 해싱
- 역할(Role): `CUSTOMER`(기본), `ADMIN`
- **내 정보 조회** — 토큰으로 인증된 본인 정보 (비밀번호는 응답에 절대 포함 금지)

### 인증
- **로그인** — 이메일 + 비밀번호 → Access Token(JWT) 발급. 만료 1시간
- 틀린 비밀번호와 없는 이메일은 **같은 에러 메시지**로 응답 ("이메일 또는 비밀번호가 올바르지 않습니다") — 이유를 생각해볼 것
- 인증 실패 → **401**, 권한 부족 → **403** (이 둘의 차이를 GlossARY 확인)

### 권한 적용 (기존 상품 API)
| API | 권한 |
|---|---|
| `POST /api/members` (가입), `POST /api/auth/login` | 누구나 |
| `GET /api/products`, `GET /api/products/{id}` | 누구나 |
| `POST/PUT/DELETE /api/products/**`, `POST .../stock` | **ADMIN만** |
| `GET /api/members/me` | 로그인한 본인 |

## API 스펙 (팀장 설계)

| Method | Path | 성공 | 실패 |
|---|---|---|---|
| POST | `/api/members` | 201 `{id, email, name, role}` | 400 (검증), **409** (이메일 중복) |
| POST | `/api/auth/login` | 200 `{accessToken, tokenType: "Bearer", expiresIn}` | 401 |
| GET | `/api/members/me` | 200 `{id, email, name, role}` | 401 |

인증 헤더 형식: `Authorization: Bearer <token>`

## 기술 결정 (ADR-004 참조)

- **JWT 라이브러리 추가 금지.** Spring Security에 내장된 `spring-boot-starter-security-oauth2-resource-server`의
  JWT 지원(`NimbusJwtDecoder` / `NimbusJwtEncoder`)을 사용한다. 검색하면 나오는 `jjwt` + 커스텀 필터 방식은
  구버전 관행이다 — 우리는 프레임워크가 제공하는 표준 경로로 간다.
- 서명 방식: **HS256 대칭키** (secret 하나로 발급·검증). 시크릿은 `application.yml`에 두되 환경변수로 덮어쓸 수
  있게 (`${JWT_SECRET:로컬용기본값}`). 32바이트 이상이어야 한다.
- 세션 없음: `SessionCreationPolicy.STATELESS`, CSRF 비활성 (토큰 기반 API라서 — 이유를 학습 노트에서 확인)
- Refresh Token은 **이번 Phase 범위 밖**. 만료되면 다시 로그인. (Phase 2.5 후보)

## 모듈 구조 (제안 — 이유를 이해하고 따를 것)

```
modules/member/                          ← 회원 도메인 (Phase 1과 같은 헥사고날)
  domain/           Member, Role, (Email/Password를 값 객체로 만들면 가산점)
  application/
    port/in/        RegisterMemberUseCase, LoginUseCase, GetMemberQuery
    port/out/       MemberRepository, PasswordHasher(!), TokenIssuer(!)
    service/
  adapter/
    in/web/         MemberController, AuthController
    out/persistence/
    out/security/   PasswordHasher 구현(BCrypt), TokenIssuer 구현(NimbusJwtEncoder)

apps/api/
  security/         SecurityConfig (SecurityFilterChain, JwtDecoder 빈, 경로별 권한 규칙)
```

**왜 `PasswordHasher`와 `TokenIssuer`가 port/out인가?** — "비밀번호를 해싱한다", "토큰을 만든다"는 도메인이
외부에 요구하는 기술 서비스다. DB와 마찬가지로 인터페이스로 격리하면 서비스 테스트에서 BCrypt(느림)나
JWT 라이브러리 없이 페이크로 대체할 수 있다. Phase 1에서 `ProductRepository`에 했던 것과 정확히 같은 원리.

**왜 SecurityConfig는 apps/api인가?** — "어떤 URL에 어떤 권한"은 모든 모듈을 조립한 뒤에 정해지는 앱 전체 정책.
`GlobalExceptionHandler`를 apps/api에 둔 것과 같은 이유.

## 구현 순서 (권장)

### Step 1 — 모듈 세팅 (힌트 없음, product 모듈 보고 직접)
- `settings.gradle.kts`에 `modules:member` 등록
- `modules/member/build.gradle.kts` 작성 (product 것을 참고하되, security 의존성 추가)
- `apps/api`가 member 모듈에 의존하도록
- **`./gradlew build` 그린 확인 후 커밋** — 빈 모듈이라도 커밋. (Phase 1 교훈: 빌드 되는 커밋)

### Step 2 — 회원가입 (Security 없이 먼저)
- 도메인 → port → service → adapter 순서 (이제 익숙할 것)
- 이메일 중복 → 409. 새 예외 타입이 필요할 텐데, **어느 모듈에 둘지** 스스로 판단하고 리뷰 때 근거를 말할 것
- `PasswordHasher` port + BCrypt 어댑터 (`BCryptPasswordEncoder`)
- 테스트: 서비스 단위 테스트 (Fake 2개: repository, hasher), 통합 테스트 (가입 201, 중복 409)

### Step 3 — Security 켜기 (여기서 혼란 시작 — 정상)
- `spring-boot-starter-security`를 추가하는 순간 **모든 API가 401**이 된다. 기존 ProductApiTest가 전부 깨진다.
- `apps/api/security/SecurityConfig`에 `SecurityFilterChain` 빈을 만들어 경로별 규칙 정의
- Spring Security 7은 **Kotlin DSL**을 지원한다:
  ```kotlin
  import org.springframework.security.config.annotation.web.invoke  // 이 import가 DSL을 켠다

  @Bean
  fun filterChain(http: HttpSecurity): SecurityFilterChain {
      http {
          csrf { disable() }
          sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
          authorizeHttpRequests {
              authorize(HttpMethod.POST, "/api/members", permitAll)
              authorize(HttpMethod.GET, "/api/products/**", permitAll)
              // ... ADMIN 규칙, anyRequest
          }
          oauth2ResourceServer { jwt { } }
      }
      return http.build()
  }
  ```
- `JwtDecoder` 빈: `NimbusJwtDecoder.withSecretKey(secretKey).build()`
- 기존 ProductApiTest 중 쓰기 API 테스트가 깨진다 → `@WithMockUser(roles = ["ADMIN"])` 또는 실제 로그인 토큰으로 수정.
  **깨진 테스트를 지우지 말 것** — 권한 규칙이 적용됐다는 증거로 "CUSTOMER가 상품 등록 시 403" 테스트를 오히려 추가

### Step 4 — 로그인 & 토큰 발급
- `TokenIssuer` port + `NimbusJwtEncoder` 어댑터 (HS256, `ImmutableSecret`)
- 토큰 claims: `sub`(memberId), `email`, `role`, `exp`
- 발급 시크릿과 검증 시크릿이 **같은 설정값**을 봐야 한다 — 어디에 두고 어떻게 공유할지 설계

### Step 5 — 권한 매핑 & `/me`
- JWT의 `role` claim → Spring Security 권한(`ROLE_ADMIN`)으로 변환: `JwtAuthenticationConverter` + `JwtGrantedAuthoritiesConverter` 설정 (기본은 `scope` claim에 `SCOPE_` 접두어를 붙이므로 **커스터마이징 필수**)
- `/api/members/me`: 컨트롤러에서 `@AuthenticationPrincipal Jwt jwt` 또는 `Authentication`으로 현재 사용자 꺼내기
- 관리자 계정 생성 경로: 가입 API로는 CUSTOMER만 만들어진다. 로컬용 ADMIN을 어떻게 만들지(예: 앱 시작 시 시드 데이터) 결정하고 리뷰 때 설명

## 수용 기준

- [ ] `./gradlew build` 그린 (전 모듈, 전 테스트)
- [ ] 가입 → 로그인 → 토큰으로 `/me` 조회, curl로 end-to-end 성공
- [ ] 토큰 없이 상품 등록 → 401 / CUSTOMER 토큰으로 상품 등록 → 403 / ADMIN 토큰 → 201
- [ ] 만료된 토큰·변조된 토큰 → 401 (변조 테스트: 토큰 마지막 글자 하나 바꿔서 요청)
- [ ] DB에 평문 비밀번호가 없다 (H2 콘솔로 직접 확인)
- [ ] `application`/`domain` 패키지에 Spring Security import가 없다 (port로 격리했는지)
- [ ] 테스트: 서비스 단위(가입, 로그인 성공/실패), 통합(가입 201/409, 로그인 200/401, 권한 401/403)
- [ ] 의미 단위 커밋, **각 커밋이 빌드되는 상태**

## 자주 빠지는 함정

1. **`@field:Email` 빼먹기** — Phase 1의 `@field:` 교훈 재확인
2. 시크릿을 코드에 하드코딩하고 커밋 — 로컬 기본값은 yml에, 실제 값은 환경변수. `.env` 류는 `.gitignore`
3. 비밀번호 검증을 도메인에서 `==`로 — 해시 비교는 `PasswordHasher.matches()`로만
4. 401과 403 혼동 — 401 "너 누구야", 403 "너인 건 알겠는데 권한 없어"
5. Security 켜고 나서 H2 콘솔·actuator까지 막혀서 당황 — 로컬 프로파일에서 permitAll 처리
6. `ProductApiTest`가 깨지자 `@SpringBootTest`에 security를 끄는 꼼수 — 금지. 권한도 테스트 대상이다

## 미리 생각해볼 질문 (리뷰 때 물어봄)

1. 왜 "없는 이메일"과 "틀린 비밀번호"를 구분해서 알려주면 안 되는가?
2. JWT는 서버가 상태를 안 갖는다고 했는데, 그럼 **로그아웃은 어떻게 구현**하는가? (완벽한 답 없음 — 트레이드오프를 말할 수 있으면 됨)
3. Access Token 만료를 1시간으로 했다. 10분이면? 24시간이면? 각각 무엇이 좋아지고 나빠지는가?

완료되면 "리뷰해줘". Phase 1처럼 실행 검증 포함해서 리뷰한다.
