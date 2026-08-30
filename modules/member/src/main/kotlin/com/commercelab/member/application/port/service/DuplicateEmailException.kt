package com.commercelab.member.application.port.service

class DuplicateEmailException : RuntimeException("중복된 이메일입니다.") {
}