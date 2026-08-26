# Commerce Lab — 드롭잇 (Dropit)

> 한정판 드롭 커머스를 만들면서 현대 백엔드 아키텍처를 경험하는 포트폴리오 프로젝트

## 무엇을 만드나

**드롭잇(Dropit)**: 소규모 브랜드가 한정 수량 상품을 "드롭"(특정 시각 오픈, 선착순 판매)하는 커머스 플랫폼.

일반 쇼핑몰 CRUD에 더해, 한정판 드롭이라는 컨셉 덕분에 **동시성 제어, 재고 정합성, 대기열** 같은
이커머스 백엔드의 핵심 난제를 자연스럽게 다루게 된다. (면접에서 이야기할 거리가 많은 구조)

## 팀 구성

| 역할 | 담당 |
|---|---|
| 개발 리더 / 아키텍처 설계 / 프론트엔드 / 코드 리뷰 | Claude |
| 백엔드 개발 (Kotlin + Spring Boot) | @seokjuhong |

## 기술 스택

- **Backend**: Kotlin 2.3 / Spring Boot 4.1 / JDK 21 / Gradle 9 (버전 카탈로그)
- **Architecture**: 멀티모듈 + 헥사고날, 모듈러 모놀리스 → 후반부 서비스 분리 실습
- **DB**: H2 (학습 초반) → PostgreSQL
- **배포**: Docker + 미니 PC + 실제 도메인 (Phase 7)

## 프로젝트 구조

```
commerce-lab/
├── apps/
│   └── api/          # 실행 가능한 Spring Boot 앱 (조립 + 실행만 담당)
├── modules/
│   ├── common/       # 공유 커널 (최소 유지)
│   └── product/      # 상품 도메인 (헥사고날 구조)
├── docs/
│   ├── ARCHITECTURE.md          # 아키텍처 설명 + 설계 결정 기록
│   ├── CURRICULUM.md            # 단계별 학습 로드맵
│   ├── GLOSSARY.md              # 이커머스 용어 사전
│   ├── kotlin-guide/            # Kotlin 문법/패턴 가이드
│   └── assignments/             # 팀장이 내주는 과제들
└── gradle/libs.versions.toml    # 의존성 버전 카탈로그
```

## 실행 방법

```bash
./gradlew :apps:api:bootRun    # 앱 실행 → http://localhost:8080
./gradlew build                # 전체 빌드 + 테스트
./gradlew :modules:product:test  # 특정 모듈만 테스트
```

- Health check: http://localhost:8080/actuator/health
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:commercelab`)

## 지금 할 일

👉 [docs/assignments/phase-1-product-crud.md](docs/assignments/phase-1-product-crud.md)
