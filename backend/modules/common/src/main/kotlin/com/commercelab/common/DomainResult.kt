package com.commercelab.common

/**
 * 도메인 연산의 결과. 성공이면 값을, 실패면 에러를 담는다.
 *
 * kotlin.Result를 쓰지 않는 이유: 그쪽은 실패 타입이 Throwable로 고정돼 있다.
 * 재고 부족 같은 비즈니스 실패를 예외로 만들면 두 가지를 잃는다.
 *   1. 시그니처만 보고 어떤 실패가 가능한지 알 수 없다 (예외는 타입에 안 드러난다)
 *   2. 예외 생성 시 스택트레이스를 수집한다. 부하 상황에서 초당 수백 번 발생하는
 *      정상적인 비즈니스 결과에 그 비용을 낼 이유가 없다
 *
 * 이름을 Result로 하지 않은 이유: kotlin.Result와 충돌해 import 순서에 따라
 * 엉뚱한 타입이 잡힌다.
 *
 * 제네릭에 붙은 out의 의미:
 *   DomainResult<OutOfStock, Order>를 DomainResult<OrderError, Order>로 취급해도 안전하다.
 *   값을 꺼내기만 하고 넣지 않는 위치이기 때문이다.
 *
 * Nothing의 의미:
 *   값이 존재할 수 없는 타입이며 모든 타입의 하위 타입이다.
 *   Success는 에러를 담지 않으므로 에러 자리가 Nothing이고, 덕분에 어떤 에러 타입의
 *   DomainResult에도 그대로 대입된다.
 */
sealed interface DomainResult<out E, out T> {

    data class Success<out T>(val value: T) : DomainResult<Nothing, T>

    data class Failure<out E>(val error: E) : DomainResult<E, Nothing>

    companion object {
        fun <T> success(value: T): DomainResult<Nothing, T> = Success(value)
        fun <E> failure(error: E): DomainResult<E, Nothing> = Failure(error)
    }
}

/** 성공 값만 변환한다. 실패면 그대로 통과시킨다. */
inline fun <E, T, R> DomainResult<E, T>.map(transform: (T) -> R): DomainResult<E, R> =
    when (this) {
        is DomainResult.Success -> DomainResult.Success(transform(value))
        is DomainResult.Failure -> this
    }

/**
 * 결과를 반환하는 연산을 이어 붙인다. 앞이 실패면 뒤는 실행되지 않는다.
 *
 * 이것이 있어서 "재고를 잡고 → 주문을 만들고 → 이벤트를 쌓는" 흐름을
 * 중간마다 if로 끊지 않고 쓸 수 있다.
 */
inline fun <E, T, R> DomainResult<E, T>.flatMap(transform: (T) -> DomainResult<E, R>): DomainResult<E, R> =
    when (this) {
        is DomainResult.Success -> transform(value)
        is DomainResult.Failure -> this
    }

/** 에러만 변환한다. 계층 경계에서 도메인 에러를 다른 표현으로 바꿀 때 쓴다. */
inline fun <E, T, F> DomainResult<E, T>.mapError(transform: (E) -> F): DomainResult<F, T> =
    when (this) {
        is DomainResult.Success -> this
        is DomainResult.Failure -> DomainResult.Failure(transform(error))
    }

/**
 * 양쪽을 하나의 값으로 접는다. 웹 어댑터에서 성공/실패를 HTTP 응답으로 바꿀 때 쓴다.
 * when으로 분기하는 것과 같지만, 두 경우를 모두 다루도록 강제된다.
 */
inline fun <E, T, R> DomainResult<E, T>.fold(
    onSuccess: (T) -> R,
    onFailure: (E) -> R,
): R = when (this) {
    is DomainResult.Success -> onSuccess(value)
    is DomainResult.Failure -> onFailure(error)
}

/** 실패일 때 부수효과만 실행한다. 로깅·메트릭에 쓴다. 결과는 그대로 흘려보낸다. */
inline fun <E, T> DomainResult<E, T>.onFailure(action: (E) -> Unit): DomainResult<E, T> {
    if (this is DomainResult.Failure) action(error)
    return this
}

/** 성공일 때 부수효과만 실행한다. */
inline fun <E, T> DomainResult<E, T>.onSuccess(action: (T) -> Unit): DomainResult<E, T> {
    if (this is DomainResult.Success) action(value)
    return this
}

fun <E, T> DomainResult<E, T>.getOrNull(): T? =
    (this as? DomainResult.Success)?.value

fun <E, T> DomainResult<E, T>.errorOrNull(): E? =
    (this as? DomainResult.Failure)?.error

val DomainResult<*, *>.isSuccess: Boolean get() = this is DomainResult.Success
val DomainResult<*, *>.isFailure: Boolean get() = this is DomainResult.Failure
