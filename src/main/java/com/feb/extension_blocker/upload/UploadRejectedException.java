package com.feb.extension_blocker.upload;

/**
 * 업로드된 파일이 검증 파이프라인의 어느 단계에서든 거부됐을 때 던진다.
 * {@link com.feb.extension_blocker.common.GlobalExceptionHandler}가 422(Unprocessable
 * Entity)로 변환한다 — 요청 자체는 문법적으로 멀쩡하지만(그래서 400은 아니다) 파일
 * 내용이 비즈니스 규칙을 위반해 처리할 수 없다는 의미라 422가 더 정확한 상태 코드다.
 */
public class UploadRejectedException extends RuntimeException {

    public UploadRejectedException(String message) {
        super(message);
    }
}
