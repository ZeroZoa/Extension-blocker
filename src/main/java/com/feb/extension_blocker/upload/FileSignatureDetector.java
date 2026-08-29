package com.feb.extension_blocker.upload;

import java.util.Set;

/**
 * 업로드된 파일의 실제 형식을 콘텐츠 바이트만으로 판별
 * (클라이언트가 보낸 MIME 타입은 절대 참고하지 않음)
 *
 * <p>세 계열은 콘텐츠만으로는 그 안의 개별 확장자를 구분할 수 없다:
 * <ul>
 *   <li>PE 계열({@code exe, com, scr, cpl}) — 전부 동일한 Windows PE(MZ) 헤더를 사용</li>
 *   <li>텍스트 계열({@code js, bat, cmd, txt, ...}) — 매직 넘버 자체가 없는 평문</li>
 *   <li>ZIP 계열({@code zip, docx, xlsx, pptx, jar}) — Office Open XML 포맷(docx 등)은
 *       내부적으로 ZIP 컨테이너라 순수 zip과 바이트 시그니처가 동일</li>
 * </ul>
 * 이 세 계열은, 콘텐츠가 그 계열에 속함을 확인한 뒤 파일명이 주장하는 확장자가 그
 * 계열의 합리적인 멤버면 그대로 실제 확장자로 인정하고, 계열 밖의 확장자를 주장하면
 * 계열 대표값으로 판별 후 위장 검사에서 필터링
 *
 * <p>위 세 계열에도, 명확한 개별 시그니처(PNG/JPG 등)에도 해당하지 않는 완전히 미지의
 * 바이너리는 파일명이 주장하는 확장자를 그대로 인정 -> 판단할 근거가 없기 때문.
 * 그렇지 않으면 시그니처를 등록해두지 않은 모든 정상 포맷이 전부 위장으로 오판되며,
 * 이는 확장자 "블랙리스트" 모델이 아닌 "화이트리스트" 모델로 변질되는 것과 같다
 */
final class FileSignatureDetector {

    private static final Set<String> PE_FAMILY = Set.of("exe", "com", "scr", "cpl");
    private static final Set<String> TEXT_FAMILY = Set.of(
            "txt", "csv", "json", "xml", "html", "htm", "css", "md",
            "log", "ini", "yml", "yaml", "js", "bat", "cmd", "sh");
    private static final Set<String> ZIP_FAMILY = Set.of("zip", "docx", "xlsx", "pptx", "jar");

    private FileSignatureDetector() {
    }

    static String detect(byte[] content, String claimedExtension) {
        if (startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "png";
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) return "jpg";
        if (startsWith(content, 0x47, 0x49, 0x46, 0x38)) return "gif";
        if (startsWith(content, 0x42, 0x4D)) return "bmp";
        if (startsWith(content, 0x25, 0x50, 0x44, 0x46)) return "pdf";
        if (startsWith(content, 0x1F, 0x8B)) return "gz";
        if (startsWith(content, 0x7F, 0x45, 0x4C, 0x46)) return "elf";

        if (startsWith(content, 0x50, 0x4B, 0x03, 0x04)
                || startsWith(content, 0x50, 0x4B, 0x05, 0x06)
                || startsWith(content, 0x50, 0x4B, 0x07, 0x08)) {
            return resolveFamilyMember(claimedExtension, ZIP_FAMILY, "zip");
        }
        if (startsWith(content, 0x4D, 0x5A)) {
            return resolveFamilyMember(claimedExtension, PE_FAMILY, "exe");
        }
        if (looksLikeText(content)) {
            return resolveFamilyMember(claimedExtension, TEXT_FAMILY, "txt");
        }

        // 미지 바이너리는 증거가 없으니 claimed를 그대로 인정, 없으면 대표값 "bin" 반환
        return claimedExtension != null ? claimedExtension : "bin";
    }

    /**
     * 콘텐츠가 특정 계열({@code family})에 속함이 이미 확인된 뒤에만 호출된다.
     * claimed가 그 계열의 멤버면 반증할 수 없으니 그대로 인정하고, 계열 밖이면(또는
     * claimed 자체가 없으면) 계열 대표값을 반환해 위장 검사에서 걸리도록 한다.
     */
    private static String resolveFamilyMember(String claimedExtension, Set<String> family, String representative) {
        return (claimedExtension != null && family.contains(claimedExtension)) ? claimedExtension : representative;
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 콘텐츠 앞부분(최대 8192바이트)이 텍스트와 유사한지 휴리스틱 기법으로 판단
     * 0x80 이상 바이트도 출력 가능 판단 -> UTF-8 멀티바이트 문자 때문임
     * 95% 기준은 완벽한 판별이 불가능한 휴리스틱이라 약간의 여윳값을 할당
     */
    private static boolean looksLikeText(byte[] content) {
        int sampleSize = Math.min(content.length, 8192);
        if (sampleSize == 0) {
            return true;
        }
        int printable = 0;
        for (int i = 0; i < sampleSize; i++) {
            int b = content[i] & 0xFF;
            if (b == 0) {
                return false;
            }
            if (b == 9 || b == 10 || b == 13 || (b >= 32 && b < 127) || b >= 128) {
                printable++;
            }
        }
        return printable >= sampleSize * 0.95;
    }
}
