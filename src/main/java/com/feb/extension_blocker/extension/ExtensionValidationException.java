package com.feb.extension_blocker.extension;

/**
 * 정책 관리 요청이 기존 데이터와 충돌할 때 던진다 — 확장자 중복, 고정/커스텀 충돌,
 * 200개 상한 초과 등. 값 자체의 형식이 잘못된 경우는
 * {@link InvalidExtensionFormatException}이 따로 담당한다(400 vs 409 구분).
 * {@link com.feb.extension_blocker.common.GlobalExceptionHandler}가 이를 받아
 * 일관된 형식의 JSON 에러 응답으로 변환한다.
 */
public class ExtensionValidationException extends RuntimeException {

    public ExtensionValidationException(String message) {
        super(message);
    }
}
