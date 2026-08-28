package com.feb.extension_blocker.extension.dto;

import com.feb.extension_blocker.extension.ExtensionPolicy;

/** 커스텀 확장자 한 건에 대한 응답 형식. */
public record CustomExtensionResponse(Long id, String extension) {

    public static CustomExtensionResponse from(ExtensionPolicy policy) {
        return new CustomExtensionResponse(policy.getId(), policy.getExtension());
    }
}
