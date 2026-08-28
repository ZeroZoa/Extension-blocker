package com.feb.extension_blocker.extension.dto;

import com.feb.extension_blocker.extension.ExtensionPolicy;

/** 고정 확장자 한 건에 대한 응답 형식. */
public record FixedExtensionResponse(String extension, boolean blocked) {

    public static FixedExtensionResponse from(ExtensionPolicy policy) {
        return new FixedExtensionResponse(policy.getExtension(), policy.isBlocked());
    }
}
