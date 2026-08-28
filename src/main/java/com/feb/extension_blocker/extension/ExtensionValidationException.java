package com.feb.extension_blocker.extension;

/**
 * 정책 관리 요청이 단순 404가 아닌 비즈니스 규칙을 위반했을 때 던진다 — 예를 들어
 * 확장자 중복, 고정/커스텀 충돌, 200개 상한 초과, 허용되지 않는 문자 등.
 * {@link com.feb.extension_blocker.common.GlobalExceptionHandler}가 이를 받아
 * 일관된 형식의 JSON 에러 응답으로 변환한다.
 */
public class ExtensionValidationException extends RuntimeException {

    public ExtensionValidationException(String message) {
        super(message);
    }
}
