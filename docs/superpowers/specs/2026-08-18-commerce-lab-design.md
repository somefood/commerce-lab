# commerce-lab 설계문서

> **관련 문서**
> [진도](../../PROGRESS.md) · [협업 규칙](../../../CLAUDE.md) · [마일스톤 문서](../../milestones/) ·
> [ADR](../../adr/) · [M0 실행계획](../plans/2026-08-18-m0-scaffolding.md)

- 작성일: 2026-08-18
- 상태: 승인됨
- 목적: 백엔드 실력을 실전 수준으로 끌어올리기 위한 학습 협업 프로젝트

## 1. 배경과 목표

AI 에이전트에 구현을 위임하면서 백엔드 근력이 정체됐다는 문제의식에서 출발한다.
AI 사용을 줄이는 대신, 역할을 재배치한다.

| 역할 | 담당 |
|---|---|
| 백엔드 구현 | 사용자 (프로덕션 코드 100%) |
| 백엔드 리더 | Claude (설계, 실패 테스트, 코드리뷰, 설계 논쟁) |
| 프론트엔드 | Claude (전담) |
| 인프라/스캐폴딩 | Claude |

성공 기준:

1. 동시성 제어, 멱등성, 이벤트 정합성, 분산 트랜잭션을 **직접 구현하여** 설명할 수 있다.
2. 각 설계 결정의 트레이드오프를 ADR로 남기고, 면접에서 근거와 함께 말할 수 있다.
3. 부하 테스트 수치(오버셀 발생 건수, p99, 처리량)를 실측 데이터로 제시할 수 있다.
4. 모놀리스 → 서비스 분리 전환을 실제로 수행하고, 그때 깨진 것을 설명할 수 있다.

비목표 (YAGNI):

- 실제 배포·운영, 실 결제사 연동, 인증/회원 시스템의 깊은 구현
- UI 완성도 (프론트는 백엔드 동작을 관찰하는 계기판 역할)

## 2. 기술 스택

| 영역 | 선택 | 근거 |
|---|---|---|
| 언어/프레임워크 | Kotlin 2.x + Spring Boot 3.x | 사용자 기존 강점 축. 국내 백엔드 공고 적합도 최상 |
| 영속성 | JPA (쓰기) + jOOQ (복잡 조회) | 애그리거트 저장은 JPA, 조회 최적화는 타입 안전 SQL로 분리 |
| DB | PostgreSQL | 스키마 분리, advisory lock, `SELECT FOR UPDATE` 지원 |
| 캐시/분산락 | Redis | 선점 신호, 분산락 실습 |
| 메시징 | Redpanda (Kafka 호환) | Kafka API 그대로, 로컬 리소스 소모 적음 |
| 테스트 | JUnit5 + Testcontainers + ArchUnit | 도메인은 순수 단위, 인프라는 실제 컨테이너 |
| 부하 | k6 (Docker 이미지) | 로컬 설치 없이 실행. 시나리오를 코드로 관리 |
| 관측 | Prometheus + Grafana | 락 경합, 커넥션 풀, 지연을 눈으로 확인 |
| 프론트 | Next.js(App Router) + TypeScript + TanStack Query + Tailwind | OpenAPI 타입 생성으로 API 계약 위반을 빌드 에러로 검출 |

## 3. 아키텍처

### 3.1 형태: Gradle 멀티모듈 모듈러 모놀리스

물리 분리 MSA로 시작하지 않는다. 근거:

1. 분산 시스템의 부수 복잡도(직렬화, 타임아웃, 로컬 실행)가 초반 학습 대역폭을 잠식한다.
2. 모놀리스의 고통을 겪지 않고 MSA로 시작하면 "왜 나눴는지"를 체감하지 못한다.
3. M4에서 직접 분리하는 경험 자체가 이 프로젝트의 최대 산출물이다.

단일 모듈 + 패키지 분리는 배제한다. 도구가 강제하지 않는 경계는 반드시 무너진다.

### 3.2 디렉터리 구조

```
commerce-lab/
├─ backend/
│  ├─ build-logic/              Gradle 컨벤션 플러그인
│  ├─ modules/
│  │  ├─ common/                Money, DomainError, Clock, IdGenerator
│  │  ├─ contract/              모듈 간 이벤트 계약 (순수 data class, 의존성 0)
│  │  ├─ order/
│  │  │  ├─ order-api/          외부에 노출되는 유일한 창구 (인터페이스만)
│  │  │  └─ order-core/         도메인 + 구현. 외부에서 컴파일 불가
│  │  └─ payment/
│  │     ├─ payment-api/
│  │     └─ payment-core/
│  └─ bootstrap/                Boot 앱, REST 어댑터, 설정, 모듈 조립
├─ frontend/                    Next.js
├─ infra/                       docker-compose, grafana, k6
└─ docs/
   ├─ adr/                      결정 기록 (사용자가 작성)
   ├─ milestones/               마일스톤 설계문서 = 작업지시서 (Claude가 작성)
   ├─ retro/                    회고
   ├─ superpowers/specs/        본 설계문서
   └─ PROGRESS.md               진도 및 학습 개념 추적
```

### 3.3 경계 강제 규칙

| 규칙 | 강제 수단 | 위반 시 |
|---|---|---|
| 모듈 간 core 직접 참조 금지 | Gradle 의존성 그래프 | 컴파일 에러 |
| `@Transactional`이 모듈 경계 초과 금지 | ArchUnit | 테스트 실패 |
| 스키마 교차 조인 금지 | 모듈별 DB 스키마 분리 | 쿼리 에러 |
| `contract` 모듈은 프레임워크 의존 금지 | ArchUnit | 테스트 실패 |

의존 방향:

```
bootstrap  → order-core, payment-core, order-api, payment-api, common, contract
order-core → order-api, common, contract
payment-core → payment-api, common, contract
contract   → (없음)
common     → (없음)
```

`order-core`와 `payment-core` 사이에는 어떤 방향의 의존도 존재하지 않는다.

## 4. 도메인 모델

### 4.1 스키마 배치

```
order 스키마                      payment 스키마
├─ orders                        ├─ accounts
├─ order_lines                   ├─ ledger_entries   (append-only)
├─ inventories                   ├─ payments         (멱등성 키 보유)
├─ reservations                  └─ outbox_messages
└─ outbox_messages
```

`orders`는 결제를 FK로 참조하지 않는다. `payments`가 `order_id`를 값으로만 보관한다.
M4에서 DB를 분리해도 코드 변경이 발생하지 않도록 하기 위함이다.

### 4.2 주문 상태머신

```
CREATED ──payment.completed──▶ PAID ──▶ SHIPPED ──▶ DELIVERED
   │                             │
   └──expired / cancelled──▶ CANCELLED ◀──payment.failed
```

### 4.3 재고 차감 정책: 선점(reservation) + TTL

세 후보를 검토했다.

| 방식 | 내용 | 성격 |
|---|---|---|
| (가) 즉시 차감 | 주문 생성 시 차감, 결제 실패 시 복원 | 보상(compensation) |
| (나) 결제 후 차감 | 결제 성공 후 차감 | 결제 성공 + 재고 없음 발생 |
| (다) 선점 + TTL | HELD 상태로 잡고 만료 시 자동 해제 | 예약(reservation) |

**(다)를 채택한다.** 근거:

1. **공정성** — 먼저 구매를 시도한 사용자의 시도를 일정 시간 보호한다.
2. **결제 수단 확장성** — 가상계좌/무통장은 입금까지 수일이 걸린다. 선점 없이는 "입금 후 품절"이 대량 발생한다.
3. **학습 가치** — 만료 처리와 확정 처리의 경쟁 조건이 M4 Saga의 축소판이다.

(가)와 (다)는 대립하는 방식이 아니라 같은 스펙트럼의 양 끝이다.
(가)는 선점 시간이 0인 (다)이며, 차이는 그 중간 상태를 명시적으로 모델링했는지 여부다.
보상 방식은 코드가 단순한 대신 되돌리는 창(window) 동안 시스템 상태가 사실과 다르다.
예약 방식은 상태가 정직한 대신 만료 관리가 추가된다.

업계 실태(설계 근거 보강): 상시판매 저경합 이커머스는 (가)가 다수, 티켓팅·한정판·호텔/항공·
가상계좌 취급 쇼핑몰은 (다)가 사실상 필수. 초대형 마켓플레이스는 실물 재고 불일치를 전제로
오버셀을 일부 허용하고 사후 취소·보상으로 처리하기도 한다.

### 4.4 선점 모델

```
inventories   : product_id, total, reserved, (available = total - reserved)
reservations  : id, order_id, product_id, qty, status, expires_at
                status: HELD → CONFIRMED | RELEASED | EXPIRED
```

**핵심 위험 — 만료와 확정의 경쟁 조건.**

> 만료 스케줄러가 예약을 해제하는 순간 결제 완료 이벤트가 도착하면,
> 재고는 타인에게 넘어갔는데 결제는 성공한 상태가 된다.

해결: `expires_at` 조건부 UPDATE(compare-and-set)로 만료 처리와 확정 처리가 서로를 배제하게 한다.
Redis TTL은 신호용 보조 수단이며, **진실의 원천은 항상 DB**다.
이 시나리오는 M1 실패 테스트에 포함한다.

### 4.5 결제 원장

`balance` 단일 컬럼을 쓰지 않는다. 복식부기 원장을 사용한다.

```
ledger_entries : id, account_id, direction(DEBIT|CREDIT), amount,
                 reference_type, reference_id, created_at   -- append-only, UPDATE 금지
payments       : id, order_id, idempotency_key(UNIQUE), status, amount
```

근거: 감사 추적 가능, 정합성 검증 가능(차변합 = 대변합), 이력 손실 없음.
잔액은 원장 합계로 계산하다가 성능 한계에 도달하면 스냅샷 테이블을 도입한다
(성능 문제를 체감한 뒤 도입하는 것이 학습 순서상 중요하다).

### 4.6 이벤트 계약

`contract` 모듈에 순수 data class로만 정의한다. 원시 타입만 사용하며 도메인 타입을 노출하지 않는다.

```kotlin
data class OrderPlaced(
    val eventId: String,      // 컨슈머 멱등성 판단 기준
    val orderId: String,
    val accountId: String,
    val amount: Long,         // Money 아님 — 계약은 원시 타입
    val occurredAt: Instant,
)
```

근거: M4에서 프로세스가 분리되면 계약은 JSON으로 직렬화된다.
도메인 타입이 계약에 새면 양쪽 서비스가 같은 클래스를 공유해야 하고, 독립 배포가 불가능해진다.

## 5. 데이터 흐름 (M3 기준)

```
POST /orders
  └─ [단일 트랜잭션]
       orders INSERT
       reservations INSERT (HELD)
       inventories.reserved UPDATE (락)
       outbox_messages INSERT          ← 여기까지 원자적
     COMMIT
          ↓
     OutboxPoller (주기 폴링)
          ↓ Kafka publish
     PaymentConsumer
          ├─ eventId 중복 → 무시 (멱등)
          └─ [트랜잭션] ledger_entries INSERT + outbox INSERT(PaymentCompleted)
                            ↓
                       OrderConsumer → reservation CONFIRMED, orders.status = PAID
```

DB 커밋과 이벤트 발행을 한 트랜잭션에 묶는다(트랜잭셔널 아웃박스).
커밋 후 브로커에 직접 발행하면 커밋 성공 + 발행 실패의 창이 생긴다.
아웃박스는 그 창을 제거하는 대신 at-least-once 전달을 낳으므로,
**컨슈머 멱등성이 선택이 아닌 필수 조건**이 된다. 두 결정은 세트다.

## 6. 에러 처리

| 계층 | 규칙 |
|---|---|
| 도메인 | 예외 대신 `Result` 타입. 비즈니스 실패는 예외가 아니다 |
| 애플리케이션 | 도메인 실패를 명시적 에러 코드로 변환 |
| 웹 어댑터 | `@RestControllerAdvice` → RFC 9457 Problem Details |
| 컨슈머 | 재시도 가능/불가 구분. 불가면 즉시 DLQ |

예외는 제어 흐름이 아니라 사고(事故)에만 사용한다.

## 7. 테스트 전략

| 종류 | 도구 | 대상 |
|---|---|---|
| 도메인 단위 | JUnit5 (스프링 없음) | 상태 전이, 금액 계산 |
| 통합 | Testcontainers | 락, 트랜잭션, 아웃박스 |
| 아키텍처 | ArchUnit | 모듈 경계, 트랜잭션 경계, 계약 순수성 |
| 계약 | OpenAPI 스냅샷 diff | API 하위호환 파괴 검출 |
| 부하 | k6 | 오버셀, 처리량, p99 |

Claude가 제공하는 실패 테스트는 도메인 단위 + 통합 테스트가 중심이며, 이것이 실행 가능한 스펙 역할을 한다.

## 8. 마일스톤

각 마일스톤은 [`docs/milestones/`](../../milestones/)에 별도 설계문서를 갖는다.

### M0 — 스캐폴딩 (Claude 전담)

Gradle 멀티모듈, docker-compose(Postgres/Redis/Redpanda/Prometheus/Grafana),
CI, ArchUnit 규칙, 헬스체크, 프론트 뼈대.

완료 기준: `docker compose up` 후 백엔드/프론트 기동, ArchUnit 통과, CI 초록.

### M1 — 주문 코어와 동시성 제어

→ 작업지시서: [docs/milestones/M1-order-core.md](../../milestones/M1-order-core.md)

주문 상태머신, 재고 차감. 의도적으로 3단계로 진화시킨다.

1. 락 없이 구현 → k6 동시 100요청 → 재고 음수 발생 관측
2. 낙관적 락(`@Version`) → 충돌 예외 급증 → 재시도 정책 수립
3. 선점(HELD) + TTL 전환 → 만료/확정 경쟁 조건 처리

3단계로 바로 진입하지 않는다. 문제를 겪고 해결한 서사가 목적이다.

학습: 격리 수준, 락 경합, 데드락, 커넥션 풀 고갈, 조건부 UPDATE.
프론트: 상품 목록, 주문, 실시간 재고/선점 상태 표시.
완료 기준: k6 동시 100요청에서 오버셀 0건, 처리량 3단계 비교표 작성, ADR 2건 이상.

### M2 — 결제 원장과 멱등성

복식부기 원장, 멱등성 키, 잔액 스냅샷.

학습: 멱등성 설계, 정합성 검증, append-only 모델링, 금융 도메인.
프론트: 지갑 잔액, 거래 내역 타임라인.
완료 기준: 동일 멱등성 키 중복 요청에서 원장 항목 1건만 생성, 차변합 = 대변합 검증 테스트 통과.

### M3 — 트랜잭셔널 아웃박스와 이벤트

아웃박스 테이블, 폴러, Kafka 발행, 컨슈머 멱등 처리, 재처리, DLQ.

학습: at-least-once의 실제 의미, 컨슈머 멱등성, 순서 보장의 한계.
프론트: 이벤트 흐름 실시간 시각화 (SSE).
완료 기준: 발행 중 강제 종료 후 재기동 시 이벤트 유실 0건, 중복 소비 시 부작용 0건.

### M4 — 물리 분리와 Saga

`payment`를 별도 프로세스 + 별도 DB로 분리. 보상 트랜잭션, 타임아웃, 정산 배치.

학습: 분산 트랜잭션, 부분 실패, 서비스 분리의 실제 비용.
프론트: 서비스 상태 대시보드, Saga 진행 시각화.
완료 기준: 분리 전후 지연/처리량 비교 데이터, 결제 서비스 강제 다운 시 보상 정상 동작.

## 9. 협업 워크플로우

### 9.1 마일스톤 사이클

```
1. Claude가 docs/milestones/MN-*.md 작성
     - 왜 이 설계인가 (배경, 트레이드오프)
     - 인터페이스 정의 (시그니처만)
     - 실패하는 테스트 코드 (실행 가능한 스펙)
     - 힌트 (접힌 상태. 정말 막혔을 때만 열람)
2. 사용자가 구현 → 전체 테스트 통과
3. Claude 코드리뷰 — 지적만 수행, 수정하지 않음
4. 사용자 수정 → 재리뷰
5. 회고를 docs/retro/에 기록, 다음 마일스톤 난이도 조정
```

### 9.2 리뷰 규칙

Claude는 답을 제시하지 않고 질문한다.

- 지양: "여기 락 범위가 넓으니 이렇게 줄이세요"
- 지향: "이 트랜잭션이 잡고 있는 락의 범위는 어디까지인가? 여기서 외부 API를 호출하면 무슨 일이 생기나?"

두 번 질문해도 해결되지 않으면 그때 설명한다.

### 9.3 ADR

설계 결정마다 `docs/adr/NNNN-제목.md`를 남긴다. 형식: 상태 / 배경 / 결정 / 대안 / 결과.

**ADR은 사용자가 작성한다.** Claude가 설명한 설계 의도를 사용자의 언어로 재기술하는 것이
이해했다는 증거이며, 이후 면접 답변의 원본이 된다.

### 9.4 설계 논쟁 의식

마일스톤마다 Claude가 정답 없는 트레이드오프 질문 1건을 제시한다.
사용자 답변 → Claude 판단 → 합의 → ADR 기록 순으로 진행한다.
(첫 사례: 재고 차감 시점. 사용자가 선점 방식을 공정성 근거로 선택했고, 채택됨.)

### 9.5 진도 추적

`docs/PROGRESS.md`에 마일스톤 체크리스트와 학습 개념 목록을 유지한다. 이직 서류 작성 시 원자료로 사용한다.

## 10. 결정 요약

| 결정 | 선택 | 버린 대안 |
|---|---|---|
| 백엔드 스택 | Kotlin + Spring Boot | Node.js/NestJS, Java |
| 도메인 | 주문 + 결제 원장 + 이벤트 파이프라인 | 티켓 예매, 인증 플랫폼 |
| 아키텍처 | 모듈러 모놀리스 → M4에서 분리 | 처음부터 MSA, 단일 모듈 |
| 재고 차감 | 선점 + TTL (M1에서 3단계 진화) | 즉시 차감, 결제 후 차감 |
| 잔액 관리 | 복식부기 원장 | balance 컬럼 UPDATE |
| 이벤트 발행 | 트랜잭셔널 아웃박스 | 커밋 후 직접 발행 |
| 인프라 | 처음부터 Docker Compose 풀셋 | H2/인메모리, 단계적 도입 |
| 협업 경계 | 설계문서 + 실패 테스트까지 Claude | 뼈대 스캐폴딩까지, 설계문서만 |

## 11. 환경 이식성

여러 머신에서 동일한 협업 규칙이 적용되도록 규칙을 레포에 고정한다.

| 위치 | git 추적 | 이식 | 용도 |
|---|---|---|---|
| `CLAUDE.md` (루트) | ✓ | ✓ | 역할 분담, 리뷰 방식, 아키텍처 불변 규칙 |
| `.claude/settings.json` | ✓ | ✓ | 권한, 훅 |
| `.claude/skills/` | ✓ | ✓ | 프로젝트 전용 스킬 |
| `.claude/settings.local.json` | ✗ | ✗ | 머신별 예외 (gitignore) |
| `~/.claude/CLAUDE.md` | ✗ | ✗ | 개인 전역 설정. 프로젝트 규칙을 여기 두지 않는다 |

원격: https://github.com/somefood/commerce-lab (public)

전역 설치 플러그인·스킬은 이식되지 않는다. 프로젝트에 필수인 것은 `.claude/skills/`에 넣어 커밋한다.

## 12. 도구 실행 환경 (M0에서 확정)

- 패키지 매니저는 npm을 쓴다 (pnpm 미설치).
- k6는 설치하지 않고 Docker 이미지 `grafana/k6`로 실행한다.
- Gradle CLI가 없으므로 모든 빌드는 `./gradlew` 래퍼로 한다 (Gradle 8.14.3).
- Grafana는 3001 포트를 쓴다. 프론트엔드가 3000을 점유하기 때문이다.
- jOOQ는 M0 범위에서 제외했다. 조회 최적화가 실제로 필요해지는 M1 3단계에서 도입한다.
