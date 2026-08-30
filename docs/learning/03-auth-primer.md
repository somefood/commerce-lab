# 인증/인가 입문 — Phase 2 시작 전에 읽기

> 사전 학습 노트. Phase 2를 끝내면 "겪은 것" 기준으로 이 문서를 다시 고쳐 쓴다.

## 1. 인증(Authentication) vs 인가(Authorization)

| | 질문 | 실패 시 HTTP |
|---|---|---|
| **인증** | "너 누구야?" — 신원 확인 | **401 Unauthorized** (이름과 달리 "미인증"의 뜻) |
| **인가** | "너인 건 알겠는데, 이거 해도 돼?" — 권한 확인 | **403 Forbidden** |

로그인은 인증, "ADMIN만 상품 등록"은 인가. Spring Security는 이 둘을 별도 단계로 처리한다.

## 2. 세션 vs 토큰 — 왜 JWT인가

### 세션 방식 (전통)
```
로그인 → 서버가 세션 저장소에 {sessionId: 회원정보} 저장 → 쿠키로 sessionId 전달
매 요청 → 쿠키의 sessionId로 저장소 조회 → 회원 확인
```
- 서버가 **상태를 가진다**. 서버가 2대면 세션 저장소를 공유해야 함 (Redis 등)
- 로그아웃 = 저장소에서 삭제. 즉시 무효화 가능

### 토큰 방식 (JWT)
```
로그인 → 서버가 회원정보를 담은 토큰에 서명해서 발급 → 클라이언트가 보관
매 요청 → Authorization: Bearer <token> → 서버는 서명만 검증 (저장소 조회 없음)
```
- 서버가 **상태를 갖지 않는다(stateless)**. 서버가 10대여도 시크릿만 같으면 됨 → 수평 확장 쉬움
- 로그아웃 = ??? 서버에 상태가 없으니 "이 토큰 무효"를 기억할 곳이 없다 → **JWT의 근본적 약점**
  - 현실 답: 만료를 짧게 + Refresh Token, 또는 블랙리스트(그 순간 stateless 포기)

이 트레이드오프가 면접 단골이다. "JWT 쓰면 무조건 좋다"가 아니라 **"확장성을 얻고 즉시 무효화를 잃는다"**를 말할 수 있어야 한다.

## 3. JWT 해부

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiIxIiwicm9sZSI6IkFETUlOIiwiZXhwIjoxNzI0OTk5OTk5fQ . 3kT9x...
       Header         .                      Payload (Claims)                     .  Signature
  {"alg":"HS256"}       {"sub":"1","role":"ADMIN","exp":1724999999}          HMAC(header.payload, secret)
```

- **Header/Payload는 암호화가 아니라 Base64 인코딩** — 누구나 디코딩해서 읽을 수 있다. 민감정보(비밀번호, 주민번호) 절대 금지
- **Signature**가 핵심 — 시크릿을 모르면 페이로드를 바꿨을 때 서명을 못 맞춘다. 서버는 "서명이 유효한가"만 보면 위변조를 잡는다
- 표준 claim: `sub`(주체, 보통 사용자 ID), `exp`(만료), `iat`(발급 시각), `iss`(발급자)
- jwt.io에서 직접 디코딩해볼 것

### 대칭키(HS256) vs 비대칭키(RS256)
- **HS256**: 시크릿 하나로 발급·검증. 단순. 발급자와 검증자가 같은 시스템일 때 (우리 케이스)
- **RS256**: 개인키로 발급, 공개키로 검증. 검증하는 쪽이 여럿(MSA)일 때 — 공개키는 노출돼도 위조 불가. Phase 8에서 서비스 분리할 때 전환 후보

## 4. 비밀번호 저장 — 해싱

- 평문 저장: DB 유출 = 전 회원 비밀번호 유출. 절대 금지
- 단순 해시(SHA-256): 빠르다는 게 오히려 약점 — 초당 수십억 번 대입 가능, 레인보우 테이블
- **BCrypt**: 의도적으로 느리고(cost factor로 조절), 자동 솔트 포함. 같은 비밀번호도 매번 다른 해시 → 이게 정답
  ```
  $2a$10$N9qo8uLOickgx2ZMRZoMye...   ← 알고리즘/cost/솔트/해시가 한 문자열에
  ```
- 검증은 "복호화해서 비교"가 아니라 **"입력을 같은 방식으로 해싱해서 비교"** (`matches(raw, hashed)`)

## 5. Spring Security 동작 원리 — 필터 체인

```
HTTP 요청
  → [SecurityFilterChain]  ← 서블릿 필터들의 묶음. 컨트롤러 앞에서 동작
      ├ BearerTokenAuthenticationFilter   Authorization 헤더에서 토큰 추출 → JwtDecoder로 검증
      │                                   → 성공 시 SecurityContext에 Authentication 저장
      ├ ...
      └ AuthorizationFilter               URL 규칙(authorizeHttpRequests)과 Authentication 대조
                                          → 미인증 401 / 권한 부족 403
  → DispatcherServlet → 컨트롤러
```

- `SecurityFilterChain` 빈 하나가 이 체인의 설계도. 우리가 `apps/api/security/SecurityConfig`에 만든다
- `SecurityContextHolder`: 현재 스레드의 인증 정보. 컨트롤러에서 `@AuthenticationPrincipal`로 꺼내는 게 이것
- Spring Boot는 security 스타터만 넣으면 **기본으로 모든 요청을 막는다** — 그래서 Step 3에서 401 폭탄을 맞는다

## 6. 왜 CSRF를 끄는가

CSRF는 "브라우저가 쿠키를 자동으로 붙이는" 성질을 악용하는 공격. 우리는 쿠키 대신 `Authorization` 헤더로 토큰을 보내고, 헤더는 브라우저가 자동으로 붙이지 않으므로 공격 벡터가 없다. → 토큰 기반 stateless API에서는 CSRF 보호를 끄는 게 표준.
(반대로, JWT를 **쿠키에** 담기로 하면 CSRF가 다시 문제가 된다 — 설계가 연결되어 있다)

## 7. 우리 구현의 경로 (Boot 4 / Security 7 기준)

| 필요한 것 | 사용할 것 | 위치 |
|---|---|---|
| 비밀번호 해싱 | `BCryptPasswordEncoder` | member `adapter/out/security` (PasswordHasher 구현) |
| 토큰 발급 | `NimbusJwtEncoder` + `ImmutableSecret` | member `adapter/out/security` (TokenIssuer 구현) |
| 토큰 검증 | `NimbusJwtDecoder.withSecretKey()` 빈 | apps/api `security/SecurityConfig` |
| URL 권한 규칙 | `SecurityFilterChain` + Kotlin DSL | apps/api `security/SecurityConfig` |
| role claim → 권한 | `JwtAuthenticationConverter` | apps/api `security/SecurityConfig` |
| 테스트에서 인증 흉내 | `@WithMockUser`, `jwt()` post-processor (`spring-security-test`) | 통합 테스트 |

**jjwt / java-jwt 같은 별도 라이브러리는 쓰지 않는다.** 검색 결과 대부분이 그 방식(+ `OncePerRequestFilter` 커스텀 필터)인데, Spring Security 5.x 시절 관행이다. 지금은 프레임워크가 표준으로 지원한다.

## 면접 예상 질문 (Phase 2 끝나면 답할 수 있어야 함)

1. 세션 대신 JWT를 선택한 이유와 포기한 것은?
2. JWT 로그아웃은 어떻게 구현하나?
3. Access Token 만료 시간은 어떻게 정했나? Refresh Token은 왜 필요한가?
4. 비밀번호를 BCrypt로 저장하는 이유는? SHA-256과의 차이는?
5. 401과 403의 차이는?
6. "없는 이메일"과 "틀린 비밀번호"를 구분해서 응답하면 안 되는 이유는? (→ 계정 존재 여부 열거 공격)
