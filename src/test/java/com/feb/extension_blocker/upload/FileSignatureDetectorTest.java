package com.feb.extension_blocker.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileSignatureDetectorTest {

    private static final byte[] PE_HEADER = {0x4D, 0x5A, 0x00, 0x00};
    private static final byte[] PNG_HEADER =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final byte[] ZIP_HEADER = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};

    @Test
    @DisplayName("과제 핵심 사례 report.jpg인데 실제 내용이 실행파일이면 real=exe로 판정해 위장을 잡아냄")
    void detectsDisguisedExecutable() {
        String real = FileSignatureDetector.detect(PE_HEADER, "jpg");

        assertEquals("exe", real, "claimed(jpg)가 PE 계열 밖이므로 대표값 exe로 판정되어야 위장 검사에서 걸린다");
    }

    @Test
    @DisplayName("PE 계열 안에서 주장한 확장자는 반증할 수 없으므로 그대로 인정")
    void peFamilyMemberIsAcceptedAsClaimed() {
        assertEquals("scr", FileSignatureDetector.detect(PE_HEADER, "scr"));
        assertEquals("cpl", FileSignatureDetector.detect(PE_HEADER, "cpl"));
    }

    @Test
    @DisplayName("텍스트 계열 안에서 주장한 확장자(js)는 그대로 인정")
    void textFamilyMemberIsAcceptedAsClaimed() {
        byte[] jsContent = "console.log('hi');".getBytes(StandardCharsets.UTF_8);

        assertEquals("js", FileSignatureDetector.detect(jsContent, "js"));
    }

    @Test
    @DisplayName("텍스트인데 텍스트 계열 밖의 확장자를 주장하면 대표값 txt로 판정해 위장을 잡아냄")
    void textClaimingNonTextExtensionIsFlagged() {
        byte[] textContent = "hello world".getBytes(StandardCharsets.UTF_8);

        assertEquals("txt", FileSignatureDetector.detect(textContent, "exe"));
    }

    @Test
    @DisplayName("docx(내부적으로 zip)를 zip 계열의 정상 멤버로 인정")
    void docxIsAcceptedAsZipFamilyMember() {
        assertEquals("docx", FileSignatureDetector.detect(ZIP_HEADER, "docx"));
    }

    @Test
    @DisplayName("확실한 개별 시그니처(PNG)는 계열 판정 없이 바로 확정")
    void knownSignatureIsDecisive() {
        assertEquals("png", FileSignatureDetector.detect(PNG_HEADER, "jpg"),
                "PNG는 시그니처가 명확하므로 claimed가 무엇이든 상관없이 png로 확정되어야 한다");
    }

    @Test
    @DisplayName("어떤 계열에도 속하지 않는 미지의 바이너리는 claimed를 그대로 인정(과도한 차단 방지)")
    void unknownBinaryIsGivenBenefitOfTheDoubt() {
        byte[] unknownBinary = {0x01, 0x02, 0x03, 0x04, (byte) 0xFF, (byte) 0xFE};

        assertEquals("hwp", FileSignatureDetector.detect(unknownBinary, "hwp"),
                "우리가 등록한 시그니처가 아니라는 이유만으로 정상 파일을 차단해서는 안 된다");
    }

    @Test
    @DisplayName("확장자 자체가 없는 미지의 바이너리는 특수 파일명 치환용 대표값 bin을 반환")
    void unknownBinaryWithoutClaimFallsBackToBin() {
        byte[] unknownBinary = {0x01, 0x02, 0x03, 0x04, (byte) 0xFF, (byte) 0xFE};

        assertEquals("bin", FileSignatureDetector.detect(unknownBinary, null));
    }
}
