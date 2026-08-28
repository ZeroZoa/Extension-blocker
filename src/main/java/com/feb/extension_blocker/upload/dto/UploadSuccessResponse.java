package com.feb.extension_blocker.upload.dto;

/** 업로드 성공 응답. {@code storedFilename}은 실제 물리 저장명(UUID)이 아니라 논리 파일명이다. */
public record UploadSuccessResponse(String storedFilename, String originalFilename, String extension) {
}
