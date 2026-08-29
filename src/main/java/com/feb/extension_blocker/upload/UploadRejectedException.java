package com.feb.extension_blocker.upload;

/**
 * 업로드된 파일이 검증 파이프라인의 어느 단계에서든 거부됐을 때 던지는 예외
 * {@link com.feb.extension_blocker.common.GlobalExceptionHandler}가
 * 422(Unprocessable Content)로 변환
 * -> 요청 형식은 올바르지만 파일 내용이 비즈니스 규칙을 위반해 처리할 수 없다는 의미
 */
public class UploadRejectedException extends RuntimeException {

    public UploadRejectedException(String message) {
        super(message);
    }
}
