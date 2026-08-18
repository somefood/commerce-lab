plugins {
    id("commerce.kotlin-conventions")
}

// 의존성 없음. 이벤트 계약은 어떤 프레임워크도 알아서는 안 된다.
// ArchUnit ContractPurity 규칙이 이를 감시한다.
