# 진도

> **문서 지도** — 이 파일이 시작점이다.
> [설계문서](./superpowers/specs/2026-08-18-commerce-lab-design.md) · [협업 규칙](../CLAUDE.md) ·
> [마일스톤](./milestones/) · [ADR](./adr/) · [인프라](../infra/README.md) · [README](../README.md)

세션 시작 시 Claude가 가장 먼저 읽는 파일이다. 현재 위치와 다음 할 일을 여기서 판단한다.

## 현재 마일스톤

**M1 — 주문 코어와 동시성 제어** — 1단계(락 없이) 구현 중. 브랜치 `m1-step1-no-lock`

- 결정 완료: Flyway 채택, 만료는 배치 단독(TTL 3분 / 주기 5초). ADR-0002·0004 작성 대기 (사용자)
- 스키마 마이그레이션 V1 적용 완료. 다음은 `order-api` 시그니처 확정 ([M1 §13](./milestones/M1-order-core.md#13-내가-할-일-체크리스트))

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
- [ ] 1단계: 락 없이 구현 → 오버셀 관측 — DDL 완료, API 시그니처 대기
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
