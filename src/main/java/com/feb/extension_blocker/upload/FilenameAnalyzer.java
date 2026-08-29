package com.feb.extension_blocker.upload;

import com.feb.extension_blocker.extension.ExtensionPolicyService;

import java.nio.charset.StandardCharsets;

/**
 * 업로드된 원본 파일명을 파일 내용은 건드리지 않고 이름만으로 분석한다.
 *
 * <ul>
 *   <li>점으로 시작하는 이름(예: {@code .env})의 앞 점은 숨김파일 마커로 보고
 *       확장자 구분자로 세지 않는다.</li>
 *   <li>이름 뒤쪽에 점이 더 있어도({@code .env.local}) 확장자를 주장하지 않음
 *       {@code local}을 확장자로 오인해 위장 검사에 걸려 정상 파일이부당하게 거부되는 경우때문</li>
 *   <li>점으로 시작하는 파일은 애초에 특수 파일명 치환 대상이라,
 *       위장 검사 자체가 의미가 없음</li>
 * </ul>
 */
public record FilenameAnalyzer(
        String basename,
        boolean startsWithDot,
        int extensionTokenCount,
        String claimedExtension
) {

    private static final int MAX_FILENAME_BYTES = 255;

    public static FilenameAnalyzer analyze(String rawFilename) {
        String name = stripDirectory(rawFilename);
        boolean leadingDot = name.startsWith(".");
        String core = leadingDot ? name.substring(1) : name;
        String[] parts = core.split("\\.", -1);
        int tokenCount = Math.max(parts.length - 1, 0);

        String claimed = null;
        if (!leadingDot && tokenCount >= 1) {
            String last = parts[parts.length - 1];
            // 등록 가능한 확장자 자체가 최대 20자이기 때문에 20자를 초과하면, 특수 파일명으로 판단
            // 확장자 없음과 동일하게 취급해 안전하게 리네임
            if (!last.isEmpty() && last.length() <= ExtensionPolicyService.CUSTOM_EXTENSION_MAX_LENGTH) {
                claimed = last.toLowerCase();
            }
        }
        return new FilenameAnalyzer(name, leadingDot, tokenCount, claimed);
    }

    /**
     * 클라이언트가 파일명에 경로 구분자를 포함해 보낼 가능성에 대비해 마지막 구분 이후만 선택(보조 방어)
     * 실제 저장 파일명은 항상 UUID로 새로 생성하므로 경로 조작에 대한 1차 방어선은 저장 단계에 있음(주 방어)
     */
    private static String stripDirectory(String rawFilename) {
        String name = rawFilename == null ? "" : rawFilename;
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return lastSeparator >= 0 ? name.substring(lastSeparator + 1) : name;
    }

    /** 점(맨 앞의 숨김파일 마커 제외) 기준으로 확장자 후보 토큰이 2개 이상이면 이중 확장자 */
    public boolean isDoubleExtension() {
        return extensionTokenCount >= 2;
    }

    /** 파일명이 확장자를 주장하는지 — {@code claimedExtension}이 있는지로 판단 */
    public boolean hasExtension() {
        return claimedExtension != null;
    }

    /** 파일명이 UTF-8 바이트 기준 255바이트를 넘는지 검사 */
    public boolean isTooLong() {
        return basename.getBytes(StandardCharsets.UTF_8).length > MAX_FILENAME_BYTES;
    }

    /** 확장자 없음 or 점으로 시작 or 파일명 과다 길이 중 하나라도 해당하면 특수 파일명으로 변경할 대상 */
    public boolean needsSpecialNaming() {
        return !hasExtension() || startsWithDot || isTooLong();
    }
}
