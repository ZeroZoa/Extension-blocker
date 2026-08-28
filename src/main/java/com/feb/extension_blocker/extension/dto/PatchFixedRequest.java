package com.feb.extension_blocker.extension.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 고정 확장자의 차단 상태를 토글하는 요청 바디.
 *
 * <p>원시 타입 {@code boolean} 대신 박싱 타입 {@code Boolean}을 쓴다. 원시 타입이었다면
 * 클라이언트가 실수로 {@code blocked} 필드를 요청 바디에서 빠뜨렸을 때 Jackson이
 * 조용히 {@code false}로 역직렬화해버린다 — 그러면 "차단 해제"를 요청한 적 없는데도
 * 필드 누락만으로 차단이 해제되는, 보안 정책 API에서 특히 위험한 침묵 실패가 발생한다.
 * {@code Boolean} + {@code @NotNull}로 두면 필드 누락이 명시적인 400 에러가 된다.
 */
public record PatchFixedRequest(
        @NotNull(message = "blocked 값은 필수입니다")
        Boolean blocked
) {
}
