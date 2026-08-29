package com.feb.extension_blocker.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code detected_extension} 컬럼은 {@code VARCHAR(20)}인데, {@link FileSignatureDetector}는
 * 미지의 바이너리에 대해 claimed 확장자를 길이 제한 없이 그대로 돌려줄 수 있다.
 * 저장 직전에 안전하게 잘리는지가 이 테스트의 핵심이다 — 안 잘리면 INSERT 자체가
 * 실패해서 정상 업로드까지 500으로 응답하게 된다.
 */
class UploadFileTest {

    @Test
    @DisplayName("20자를 넘는 detectedExtension은 20자로 잘려서 저장된다")
    void truncatesOverlongDetectedExtension() {
        String overlong = "a".repeat(30);

        UploadFile file = new UploadFile("weird." + overlong, "stored.bin", overlong, UploadStatus.SUCCESS, null);

        assertEquals(20, file.getDetectedExtension().length());
        assertEquals("a".repeat(20), file.getDetectedExtension());
    }

    @Test
    @DisplayName("정확히 20자인 detectedExtension은 그대로 유지된다")
    void keepsExactlyMaxLengthDetectedExtension() {
        String exactly20 = "a".repeat(20);

        UploadFile file = new UploadFile("name.ext", "stored.bin", exactly20, UploadStatus.SUCCESS, null);

        assertEquals(exactly20, file.getDetectedExtension());
    }

    @Test
    @DisplayName("detectedExtension이 null이면 잘라내지 않고 null 그대로 둔다")
    void keepsNullDetectedExtensionAsNull() {
        UploadFile file = new UploadFile("noext", null, null, UploadStatus.REJECTED, "허용되지 않는 파일명입니다");

        assertNull(file.getDetectedExtension());
    }

    @Test
    @DisplayName("500자를 넘는 originalFilename은 500자로 잘려서 저장된다")
    void truncatesOverlongOriginalFilename() {
        // null byte 검사처럼 FilenameAnalyzer(길이 인지)를 거치기 전에 원본 파일명을
        // 그대로 넘기는 거부 경로가 있어, 여기서도 같은 방어가 필요하다.
        String overlong = "a".repeat(600);

        UploadFile file = new UploadFile(overlong, null, null, UploadStatus.REJECTED, "허용되지 않는 파일명입니다");

        assertEquals(500, file.getOriginalFilename().length());
    }
}
