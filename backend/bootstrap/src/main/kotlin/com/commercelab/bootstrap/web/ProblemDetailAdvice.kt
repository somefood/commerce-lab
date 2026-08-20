package com.commercelab.bootstrap.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ProblemDetailAdvice {

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(body)
    }
}