# ADR-0005: 도메인 실패를 예외로 표현한다 (값으로 갔다가 뒤집었다)

## 상태

채택 (2026-08-24) — 2026-08-23의 "값으로 표현한다" 결정을 번복한 것이다

<!-- 번복을 별도 ADR로 쪼개지 않는 이유도 여기 한 줄로 남겨두면 좋다.
     (0005를 문서로 쓴 적이 없으므로 supersede할 대상이 없다) -->

## 배경

<!-- 사용자가 채운다: 어떤 문제 상황이었는지
     - 재고 부족을 호출자에게 어떻게 알릴 것인가
     - "일어나면 버그인 것"과 "매일 일어나는 정상적인 실패"는 같은 취급을 받아야 하나?
     - 이걸 정하지 않으면 무엇이 곤란해지나? -->

## 결정

<!-- 사용자가 채운다: 무엇을 하기로 했는지. 한 문장으로 시작할 것
     함께 정한 것들 — 각각 왜 그렇게 정했는지 쓸 것:
       - 예외 계층을 sealed로 유지한다
       - 스택트레이스를 끈다 (writableStackTrace=false)
       - OrderException을 상속하지 않는 예외는 500으로 나간다
       - 프레임워크(ErrorResponseException 등)를 상속하지 않는다 -->

## 대안

<!-- 사용자가 채운다: 무엇을 버렸고 왜
     ★ 이 ADR의 핵심이 여기다. 너는 두 방식을 다 만들어봤다.

     (가) DomainResult<E, T>로 값 반환 — 8/23에 채택했다가 8/24에 버림
          버린 이유를 순서대로: 무엇을 만들었고 → 무엇을 실측했고 → 왜 안 되겠다고 판단했나
          (롤백이 안 됐다. 실측 증상: 성공 1건인데 orders=2)
          읽기/쓰기 분할로 막았는데, 그 규율이 어디서 깨질 것 같았나?

     (나) kotlin.Result — 왜 후보에서 일찍 탈락했나

     (다) setRollbackOnly() — 값 방식을 유지하면서 롤백을 얻는 길이었다. 왜 버렸나 -->

## 결과

<!-- 사용자가 채운다: 무엇을 얻고 무엇을 포기했는지
     얻은 것과 포기한 것을 같은 무게로 쓸 것. 포기한 쪽 힌트:
       - 시그니처만 보고 어떤 실패가 가능한지 알 수 없다 (Kotlin에 checked exception이 없다)
       - 테스트가 길어졌다 (data class의 equals가 사라졌다)
     되찾은 것도 있다 — 어떻게 되찾았나?

     ※ springdoc 이득은 "따라온 것"이지 뒤집은 "이유"가 아니다. 순서를 섞지 말 것 -->

## 관련

- 설계 근거와 실측: [M1 §4-2](../milestones/M1-order-core.md)
- 커밋: `be79e97` — 전환 전후를 `git show`로 대조할 수 있다
- 구현: `order-api/.../OrderException.kt`, `bootstrap/.../OrderProblems.kt`,
  `bootstrap/.../ProblemDetailAdvice.kt`
- 증거 테스트: `bootstrap/src/test/.../OrderRollbackIntegrationTest.kt`
- 삭제된 이전 방식: `git show 9367f2b:backend/modules/common/src/main/kotlin/com/commercelab/common/DomainResult.kt`

---

**작성 안내:** 이 ADR은 사용자가 직접 채운다. Claude가 설명한 설계 의도를
자기 언어로 옮겨 적는 것이 이해했다는 증거이며, 면접 답변의 원본이 된다.
M1 §4-2를 먼저 열지 말 것 — 막히는 지점이 곧 아직 이해 못 한 부분이다.
