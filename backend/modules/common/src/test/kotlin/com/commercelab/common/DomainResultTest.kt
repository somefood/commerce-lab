package com.commercelab.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainResultTest {

    // 테스트용 에러. 실제 도메인 에러는 각 모듈이 자기 것을 정의한다.
    sealed interface TestError {
        data object NotFound : TestError
        data class Invalid(val reason: String) : TestError
    }

    @Test
    fun `성공은 값을 담고 실패는 에러를 담는다`() {
        val success: DomainResult<TestError, Int> = DomainResult.success(42)
        val failure: DomainResult<TestError, Int> = DomainResult.failure(TestError.NotFound)

        assertEquals(42, success.getOrNull())
        assertNull(success.errorOrNull())
        assertNull(failure.getOrNull())
        assertEquals(TestError.NotFound, failure.errorOrNull())
    }

    @Test
    fun `map은 성공 값만 변환한다`() {
        val success: DomainResult<TestError, Int> = DomainResult.success(21)
        assertEquals(42, success.map { it * 2 }.getOrNull())
    }

    @Test
    fun `map은 실패를 건드리지 않는다`() {
        val failure: DomainResult<TestError, Int> = DomainResult.failure(TestError.NotFound)
        var 변환이_실행됨 = false

        val result = failure.map { 변환이_실행됨 = true; it * 2 }

        assertEquals(false, 변환이_실행됨, "실패에서는 변환 함수가 실행되면 안 된다")
        assertEquals(TestError.NotFound, result.errorOrNull())
    }

    @Test
    fun `flatMap은 첫 실패에서 멈춘다`() {
        var 두번째_단계_실행됨 = false

        val result = DomainResult.success(1)
            .flatMap<TestError, Int, Int> { DomainResult.failure(TestError.Invalid("첫 단계 실패")) }
            .flatMap { 두번째_단계_실행됨 = true; DomainResult.success(it + 1) }

        assertEquals(false, 두번째_단계_실행됨, "앞 단계가 실패하면 뒤는 실행되지 않아야 한다")
        assertEquals(TestError.Invalid("첫 단계 실패"), result.errorOrNull())
    }

    @Test
    fun `flatMap은 모두 성공하면 마지막 값을 준다`() {
        val result: DomainResult<TestError, Int> = DomainResult.success(1)
            .flatMap<TestError, Int, Int> { DomainResult.success(it + 1) }
            .flatMap { DomainResult.success(it * 10) }

        assertEquals(20, result.getOrNull())
    }

    @Test
    fun `fold는 양쪽을 하나의 값으로 접는다`() {
        val success: DomainResult<TestError, Int> = DomainResult.success(7)
        val failure: DomainResult<TestError, Int> = DomainResult.failure(TestError.Invalid("음수"))

        assertEquals("값: 7", success.fold({ "값: $it" }, { "에러: $it" }))
        assertEquals("에러: Invalid(reason=음수)", failure.fold({ "값: $it" }, { "에러: $it" }))
    }

    @Test
    fun `mapError는 에러만 변환한다`() {
        val failure: DomainResult<TestError, Int> = DomainResult.failure(TestError.NotFound)
        assertEquals("찾을 수 없음", failure.mapError { "찾을 수 없음" }.errorOrNull())

        val success: DomainResult<TestError, Int> = DomainResult.success(1)
        assertEquals(1, success.mapError { "변환되면 안 됨" }.getOrNull())
    }

    @Test
    fun `onFailure와 onSuccess는 결과를 그대로 흘려보낸다`() {
        val 기록 = mutableListOf<String>()

        val result: DomainResult<TestError, Int> = DomainResult.success(1)
            .onSuccess { 기록 += "성공 $it" }
            .onFailure { 기록 += "실패 $it" }

        assertEquals(listOf("성공 1"), 기록)
        assertEquals(1, result.getOrNull())
    }

    @Test
    fun `구체 에러 타입은 상위 에러 타입으로 취급된다`() {
        // out E 덕분에 가능하다. 이게 안 되면 포트마다 정확한 에러 타입을 맞춰야 한다.
        val 구체적: DomainResult<TestError.NotFound, Int> = DomainResult.failure(TestError.NotFound)
        val 상위로: DomainResult<TestError, Int> = 구체적

        assertTrue(상위로.isFailure)
    }
}
