package com.feb.extension_blocker.extension;

/**
 * 커스텀 확장자 입력값이 형식 규칙(빈 값, 길이 초과, 영문/숫자 이외 문자)을 위반했을 때
 * 던진다. {@link ExtensionValidationException}과 분리한 이유: 저건 "값 자체는 유효한데
 * 다른 데이터와 충돌"하는 경우(중복, 상한 초과)라 409가 맞고, 이건 "값 자체가 애초에
 * 유효하지 않은" 경우라 400이 맞다 — 성격이 다른 두 에러를 같은 상태 코드로 뭉뚱그리지
 * 않기 위해 나눴다.
 */
public class InvalidExtensionFormatException extends RuntimeException {

    public InvalidExtensionFormatException(String message) {
        super(message);
    }
}
