package com.feb.extension_blocker.common;

import com.feb.extension_blocker.extension.ExtensionValidationException;
import com.feb.extension_blocker.extension.InvalidExtensionFormatException;
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
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 도메인 예외를 {@link ErrorResponse} JSON 형식으로 통일
 * -> 프론트엔드는 엔드포인트별 분기 없이 메시지만 꺼내 쓰면 됨
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * {@link ExtensionValidationException} -> 409. 값 자체는 유효하지만 기존 데이터와
     * 충돌할 때의 예외(확장자 중복, 200개 상한 초과 등)
     */
    @ExceptionHandler(ExtensionValidationException.class)
    public ResponseEntity<ErrorResponse> handleExtensionValidation(ExtensionValidationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    /**
     * {@link InvalidExtensionFormatException} -> 400. 커스텀 확장자 입력값 자체가 형식
     * 규칙(빈 값, 길이 초과, 영문/숫자 이외 문자)을 어겼을 때의 예외
     * 충돌이 아니라 애초에 값이 유효하지 않은 경우라 {@link ExtensionValidationException}과 분리함
     * 409가 아닌 400으로 응답한다.
     */
    @ExceptionHandler(InvalidExtensionFormatException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFormat(InvalidExtensionFormatException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /**
     * {@link ResponseStatusException} -> 예외가 이미 들고 있는 상태 코드/사유를 그대로 전달
     * 서비스 계층에서 "존재하지 않는 고정/커스텀 확장자"처럼 상태 코드가 명확한 경우(404) 예외처리용
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorResponse(e.getReason()));
    }

    /**
     * {@code @Valid}(예: {@code @NotBlank}, {@code @NotNull}) 검증 실패 -> 400.
     * 필드가 여러 개 동시에 실패할 수 있지만, 이 API는 한 번에 메시지 하나만 보여주는
     * 구조라 첫 번째 필드 에러만 꺼내 반환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("요청 값이 올바르지 않습니다");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    /** JSON 파싱 자체가 실패(빈 바디, 문법 오류 등) -> 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("요청 본문을 읽을 수 없습니다"));
    }

    /** 경로 변수 타입 변환 실패(예: {@code Long} 자리에 {@code /custom/abc}) -> 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("요청 값의 형식이 올바르지 않습니다"));
    }

    /**
     * {@link UploadRejectedException} -> 422. 요청 형식(JSON, multipart 등) 자체는 정상이지만
     * 파일 내용이 검증 규칙을 위반해 처리할 수 없다는 의미라 422
     */
    @ExceptionHandler(UploadRejectedException.class)
    public ResponseEntity<ErrorResponse> handleUploadRejected(UploadRejectedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new ErrorResponse(e.getMessage()));
    }

    /** 요청 자체가 멀티파트 형식이 아님 -> 400. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleNotMultipart(MultipartException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("파일이 첨부되지 않았습니다"));
    }

    /** 멀티파트 요청은 맞지만 필수 파트({@code file})가 없음 -> 400. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingFilePart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("파일이 첨부되지 않았습니다"));
    }

    /** 업로드 크기가 {@code max-file-size} 초과 -> 413. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse("파일 크기가 너무 큽니다"));
    }

    /** 위에서 안 잡힌 모든 예외(DB 커넥션 실패, NPE 등)의 최종 방어선 -> 500(원인은 로그에만, 클라이언트엔 일반 메시지). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return ResponseEntity.internalServerError().body(new ErrorResponse("서버 오류가 발생했습니다"));
    }
}
