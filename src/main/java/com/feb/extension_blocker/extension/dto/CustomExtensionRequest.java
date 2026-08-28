package com.feb.extension_blocker.extension.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 커스텀 확장자 추가 요청 바디.
 *
 * <p>{@code @NotBlank}만 걸어서 널/빈 값/공백뿐인 입력을 컨트롤러 진입 시점에 바로
 * 걸러낸다. 그 이상의 검증(trim 후 최대 길이, 허용 문자셋)은 일부러 여기 넣지 않았다 —
 * 이 서비스는 앞뒤 공백이 섞인 입력("  sh  ")을 trim해서 받아주기로 했는데, Bean
 * Validation은 컨트롤러 진입 "전", 즉 trim 되기 전 원본 문자열을 검사한다. 그래서
 * trim 이후를 전제로 하는 규칙(길이, 문자셋)을 여기서 검사하면 trim하면 멀쩡해질
 * 입력까지 조기에 거부해버리는 모순이 생긴다. 그런 규칙은 전부
 * {@code ExtensionPolicyService.normalize()}에 남겨두고, 여기서는 trim과 무관하게
 * 항상 참인 규칙만 검사한다.
 */
public record CustomExtensionRequest(
        @NotBlank(message = "확장자를 입력해주세요")
        String extension
) {
}
