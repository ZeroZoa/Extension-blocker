package com.feb.extension_blocker.common;

/** 이 API의 모든 4xx 응답이 공통으로 사용하는 에러 응답 형식. */
public record ErrorResponse(String message) {
}
