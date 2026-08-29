package com.feb.extension_blocker.common;

/** API의 모든 에러 응답(4xx/5xx)이 공통으로 사용하는 형식. */
public record ErrorResponse(String message) {
}
