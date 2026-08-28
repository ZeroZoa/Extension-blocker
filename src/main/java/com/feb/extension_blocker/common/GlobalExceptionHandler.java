package com.feb.extension_blocker.common;

import com.feb.extension_blocker.extension.ExtensionValidationException;
import com.feb.extension_blocker.upload.UploadRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 도메인 예외를 일관된 {@link ErrorResponse} JSON 형식 하나로 변환한다.
 * 프론트엔드가 어떤 엔드포인트가 실패했는지에 따라 분기하지 않고도
 * 응답에서 사람이 읽을 수 있는 메시지를 그대로 꺼내 쓸 수 있게 하기 위함이다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ExtensionValidationException.class)
    public ResponseEntity<ErrorResponse> handleExtensionValidation(ExtensionValidationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorResponse(e.getReason()));
    }

    /**
     * {@code @Valid}로 걸어둔 Bean Validation(예: {@code @NotBlank}, {@code @NotNull})이
     * 실패했을 때 Spring이 던지는 예외를 처리한다. 필드가 여러 개 동시에 실패할 수 있지만,
     * 이 API는 한 번에 하나의 사용자 메시지만 보여주는 구조라 첫 번째 필드 에러 메시지만
     * 꺼내 반환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("요청 값이 올바르지 않습니다");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    /**
     * 요청 바디가 JSON으로 파싱조차 안 되는 경우(빈 바디, 문법 오류, 타입 불일치 등)를
     * 처리한다. 이 파싱 실패는 {@code @Valid}가 개입하기도 전에 발생하므로 별도 핸들러가
     * 필요하다 — 없으면 catch-all({@link #handleUnexpected})로 떨어져 500으로 응답하게
     * 되는데, 클라이언트 잘못으로 인한 요청 오류는 500이 아니라 400이 맞다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("요청 본문을 읽을 수 없습니다"));
    }

    @ExceptionHandler(UploadRejectedException.class)
    public ResponseEntity<ErrorResponse> handleUploadRejected(UploadRejectedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new ErrorResponse(e.getMessage()));
    }

    /**
     * 업로드 파일이 {@code spring.servlet.multipart.max-file-size}(또는
     * {@code max-request-size})를 넘었을 때 Spring이 던지는 예외를 처리한다. 이 핸들러가
     * 없으면 catch-all({@link #handleUnexpected})로 떨어져 "서버 오류가 발생했습니다"라는
     * 엉뚱한 500 메시지가 나가는데, 이건 서버 잘못이 아니라 클라이언트가 큰 파일을
     * 보낸 것뿐이므로 413(Payload Too Large)과 명확한 사유가 맞다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse("파일 크기가 너무 큽니다"));
    }

    /**
     * 위 핸들러들이 잡지 않는 모든 예외(DB 커넥션 실패, NPE 등 예상 못 한 에러)의
     * 최종 방어선. 이게 없으면 Spring 기본 에러 핸들러가 대신 응답하게 되어, API가
     * 항상 {@link ErrorResponse} 형식으로 응답한다는 계약이 깨지고 설정에 따라 내부
     * 정보(스택트레이스 등)가 클라이언트에 노출될 수 있다. 실제 원인은 서버 로그에만
     * 남기고, 클라이언트에는 원인을 특정할 수 없는 일반 메시지만 반환한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return ResponseEntity.internalServerError().body(new ErrorResponse("서버 오류가 발생했습니다"));
    }
}
