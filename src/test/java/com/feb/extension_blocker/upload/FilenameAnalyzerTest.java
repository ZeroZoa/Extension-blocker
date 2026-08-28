package com.feb.extension_blocker.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilenameAnalyzerTest {

    @Test
    @DisplayName("단일 확장자는 그대로 claimedExtension으로 인식")
    void singleExtension() {
        FilenameAnalyzer analyzer = FilenameAnalyzer.analyze("report.pdf");

        assertTrue(analyzer.hasExtension());
        assertEquals("pdf", analyzer.claimedExtension());
        assertFalse(analyzer.isDoubleExtension());
        assertFalse(analyzer.needsSpecialNaming());
    }

    @Test
    @DisplayName("확장자는 대소문자와 무관하게 소문자로 정규화")
    void extensionIsLowercased() {
        FilenameAnalyzer analyzer = FilenameAnalyzer.analyze("REPORT.PDF");

        assertEquals("pdf", analyzer.claimedExtension());
    }

    @Test
    @DisplayName("점이 2개 이상이면 이중 확장자로 판정")
    void doubleExtension() {
        FilenameAnalyzer analyzer = FilenameAnalyzer.analyze("invoice.pdf.exe");

        assertTrue(analyzer.isDoubleExtension());
    }

    @Test
    @DisplayName("점으로 시작하는 파일은 확장자가 없는 것으로 취급하고 이중 확장자도 아님")
    void hiddenFileWithoutInnerDot() {
        FilenameAnalyzer analyzer = FilenameAnalyzer.analyze(".env");

        assertTrue(analyzer.startsWithDot());
        assertFalse(analyzer.hasExtension());
        assertFalse(analyzer.isDoubleExtension());
        assertTrue(analyzer.needsSpecialNaming());
    }

    @Test
    @DisplayName(".env.local처럼 점으로 시작하면서 내부에 점이 더 있어도 확장자를 주장하지 않음")
    void hiddenFileWithInnerDotDoesNotClaimExtension() {
        // 이전 기수 제출 코드는 이 케이스에서 "local"을 확장자로 오인해, 정상 파일이
        // 3단계 확장자 위장 검사에 걸려 부당하게 거부되는 버그가 있었다. 이 테스트는
        // 그 버그가 재발하지 않는지 확인한다.
        FilenameAnalyzer analyzer = FilenameAnalyzer.analyze(".env.local");

        assertTrue(analyzer.startsWithDot());
        assertFalse(analyzer.hasExtension(), "점으로 시작하면 내부에 점이 더 있어도 확장자를 주장해서는 안 된다");
        assertFalse(analyzer.isDoubleExtension(), "숨김파일 마커를 제외하면 내부 점은 1개뿐이라 이중 확장자가 아니다");
        assertTrue(analyzer.needsSpecialNaming());
    }

    @Test
    @DisplayName("확장자가 아예 없는 파일명은 확장자 없음으로 판정")
    void noExtensionAtAll() {
        FilenameAnalyzer analyzer = FilenameAnalyzer.analyze("README");

        assertFalse(analyzer.hasExtension());
        assertTrue(analyzer.needsSpecialNaming());
    }

    @Test
    @DisplayName("마지막이 점으로 끝나(file.) 확장자 토큰이 비어있으면 확장자 없음으로 판정")
    void trailingDotWithEmptyExtension() {
        FilenameAnalyzer analyzer = FilenameAnalyzer.analyze("file.");

        assertFalse(analyzer.hasExtension());
    }

    @Test
    @DisplayName("경로 구분자가 섞여 있어도 마지막 구분자 이후만 파일명으로 취급")
    void directoryIsStripped() {
        FilenameAnalyzer unix = FilenameAnalyzer.analyze("some/path/report.pdf");
        FilenameAnalyzer windows = FilenameAnalyzer.analyze("C:\\folder\\report.pdf");

        assertEquals("report.pdf", unix.basename());
        assertEquals("report.pdf", windows.basename());
    }

    @Test
    @DisplayName("UTF-8 바이트 기준 255바이트를 넘으면 과다 길이로 판정")
    void tooLongByUtf8Bytes() {
        String longName = "a".repeat(300) + ".txt";

        FilenameAnalyzer analyzer = FilenameAnalyzer.analyze(longName);

        assertTrue(analyzer.isTooLong());
        assertTrue(analyzer.needsSpecialNaming());
    }

    @Test
    @DisplayName("멀티바이트 문자는 문자 수가 아니라 바이트 수로 판단해야 정확")
    void multiByteCharactersCountAsMoreBytes() {
        // 한글 한 글자는 UTF-8에서 3바이트다. 이 파일명은 문자 수로는 100자 미만이지만
        // 바이트 수로는 255바이트를 넘는다 — 만약 구현이 문자 수 기준으로 판단했다면
        // 이 테스트는 실패해야 정상이다.
        String koreanName = "가".repeat(90) + ".txt";

        FilenameAnalyzer analyzer = FilenameAnalyzer.analyze(koreanName);

        assertTrue(koreanName.length() < 255, "문자 수 자체는 255 미만이어야 이 테스트의 의도가 성립한다");
        assertTrue(analyzer.isTooLong(), "바이트 수 기준으로는 255바이트를 넘어야 한다");
    }
}
