package com.feb.extension_blocker.upload;

import java.nio.charset.StandardCharsets;

/**
 * 업로드된 원본 파일명을 파일 내용은 건드리지 않고 이름만으로 분석한다.
 *
 * <p>점(.)으로 시작하는 이름(예: {@code .env})은 앞의 점을 숨김파일 마커로 보고 확장자
 * 구분자로 세지 않는다. 이때 이름 뒤쪽에 점이 더 있어도({@code .env.local}) 확장자를
 * "주장"하지 않는 것으로 처리한다 — 그렇지 않으면 {@code .env.local}이 {@code local}을
 * 확장자로 주장한 것으로 오인되어, 매직넘버 기반 확장자 위장 검사에 걸려 정상 파일인데도
 * 거부되는 문제가 생긴다. 점으로 시작하는 파일은 애초에 특수 파일명 치환 대상이므로
 * 확장자 위장 검사 자체가 의미가 없다.
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
            if (!last.isEmpty()) {
                claimed = last.toLowerCase();
            }
        }
        return new FilenameAnalyzer(name, leadingDot, tokenCount, claimed);
    }

    /**
     * 클라이언트가 파일명에 경로 구분자를 포함해 보낼 가능성에 대비해 마지막 구분자
     * 이후만 취한다. 다만 이건 보조 방어다 — 실제 저장 파일명은 항상 UUID로 새로 생성하므로
     * 경로 조작에 대한 1차 방어선은 저장 단계에 있다.
     */
    private static String stripDirectory(String rawFilename) {
        String name = rawFilename == null ? "" : rawFilename;
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return lastSeparator >= 0 ? name.substring(lastSeparator + 1) : name;
    }

    /** 점(맨 앞의 숨김파일 마커 제외) 기준으로 확장자 후보 토큰이 2개 이상이면 이중 확장자다. */
    public boolean isDoubleExtension() {
        return extensionTokenCount >= 2;
    }

    public boolean hasExtension() {
        return claimedExtension != null;
    }

    /**
     * 파일명이 UTF-8 바이트 기준 255바이트를 넘는지 검사한다. 문자 수가 아니라 바이트 수로
     * 재는 이유: 대부분의 리눅스 파일시스템(NAME_MAX)이 파일명 길이를 바이트 단위로
     * 제한하고, 한글 등 멀티바이트 문자가 섞이면 문자 수 기준 검사로는 실제로 저장 가능한
     * 길이인지 보장할 수 없기 때문이다.
     */
    public boolean isTooLong() {
        return basename.getBytes(StandardCharsets.UTF_8).length > MAX_FILENAME_BYTES;
    }

    /** 확장자 없음 / 점으로 시작 / 파일명 과다 길이 중 하나라도 해당하면 특수 파일명 치환 대상이다. */
    public boolean needsSpecialNaming() {
        return !hasExtension() || startsWithDot || isTooLong();
    }
}
