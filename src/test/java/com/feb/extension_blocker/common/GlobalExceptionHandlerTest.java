package com.feb.extension_blocker.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GlobalExceptionHandler}가 예외를 올바른 상태 코드로 변환하는지 검증한다.
 *
 * <p>{@code MultipartException}/{@code MissingServletRequestPartException} 케이스는
 * 전용 핸들러가 없어 catch-all(500)로 새고 있던 걸 실제 요청으로 재현해 잡은 버그라,
 * 재발 방지를 위해 회귀 테스트로 남긴다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("요청 자체가 멀티파트 형식이 아니면 400")
    void handlesNotMultipart() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotMultipart(new MultipartException("Current request is not a multipart request"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("파일이 첨부되지 않았습니다", response.getBody().message());
    }

    @Test
    @DisplayName("멀티파트 요청은 맞지만 file 파트가 없으면 400")
    void handlesMissingFilePart() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMissingFilePart(new MissingServletRequestPartException("file"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("파일이 첨부되지 않았습니다", response.getBody().message());
    }

    @Test
    @DisplayName("위에서 처리되지 않은 예외는 500(catch-all)")
    void handlesUnexpectedException() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("서버 오류가 발생했습니다", response.getBody().message());
    }
}
