package com.commercelab.member.domain

class DuplicateEmailException : RuntimeException("중복된 이메일입니다.") {
}