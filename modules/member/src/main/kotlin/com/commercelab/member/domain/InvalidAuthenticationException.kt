package com.commercelab.member.domain

class InvalidAuthenticationException : RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다.") {
}