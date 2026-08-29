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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 도메인 예외를 {@link ErrorResponse} JSON 형식으로 통일
 * -> 프론트엔드는 엔드포인트별 분기 없이 메시지만 꺼내 쓰면 됨
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
     * Validation({@code @NotBlank}, {@code @NotNull})이 실패했을 때 Spring이 던지는 예외를 처리
     * 필드가 여러 개 동시에 실패할 수 있지만, 현재는 한 번에 하나의 사용자 메시지만 보여주는 구조
     * -> 첫 번째 필드 에러 메시지만 꺼내 반환
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
     * JSON 파싱 자체가 실패한 경우 처리
     * {@code @Valid} 개입 전에 발생. 클라이언트의 잘못된 요청이기 때문에 400으로 처리
     * (안 잡으면 catch-all이 500으로 응답)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("요청 본문을 읽을 수 없습니다"));
    }

    /**
     * 경로 변수를 선언된 타입으로 변환 실패(예: {@code Long} 자리에 {@code /custom/abc}) 처리
     * 클라이언트의 잘못된 요청이기 때문에 400으로 처리 (안 잡으면 catch-all이 500으로 응답)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("요청 값의 형식이 올바르지 않습니다"));
    }

    @ExceptionHandler(UploadRejectedException.class)
    public ResponseEntity<ErrorResponse> handleUploadRejected(UploadRejectedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new ErrorResponse(e.getMessage()));
    }

    /**
     * 업로드 파일이 {@code max-file-size}를 넘었을 때 처리
     * 허용 크기를 초과한 요청이기 때문에 413으로 처리 (안 잡으면 catch-all이 500으로 응답)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse("파일 크기가 너무 큽니다"));
    }

    /**
     * 위 핸들러들이 못 잡는 모든 예외(DB 커넥션 실패, NPE 등)의 최종 방어선
     * -> 원인은 로그에만 남기고, 클라이언트에는 일반 메시지만 반환(내부 정보 노출 방지)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return ResponseEntity.internalServerError().body(new ErrorResponse("서버 오류가 발생했습니다"));
    }
}
