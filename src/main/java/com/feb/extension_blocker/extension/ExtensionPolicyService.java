package com.feb.extension_blocker.extension;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 확장자 차단 정책을 관리하는 비즈니스 로직.
 *
 * <p>{@link #getCurrentlyBlockedExtensions()}는 항상 DB에서 새로 읽어온다 — 캐싱하지
 * 않는다. 업로드 파이프라인은 정책이 방금 바뀐 경우에도 바로 다음 요청에서 그 변경을
 * 반영해야 하기 때문이다. 캐싱했다면 1초 전에 막 차단된 확장자가 캐시가 만료될 때까지
 * 계속 통과해버릴 수 있다.
 */
@Service
public class ExtensionPolicyService {

    /**
     * 커스텀 확장자 입력 최대 길이 20자. {@code public}인 이유: 업로드 파이프라인의
     * {@link com.feb.extension_blocker.upload.FilenameAnalyzer}가 "등록 가능한 확장자
     * 길이"와 "파일명에서 확장자로 인정하는 길이" 기준을 일부러 동일하게 맞추면서 이
     * 상수를 그대로 참조한다 — 두 값이 각자 하드코딩되어 있으면 한쪽만 바뀌었을 때
     * 조용히 어긋날 수 있다.
     */
    public static final int CUSTOM_EXTENSION_MAX_LENGTH = 20;
    /** 커스텀 확장자 최대 200개까지 추가 가능 */
    private static final int CUSTOM_EXTENSION_MAX_COUNT = 200;
    /** Pattern은 한 번만 컴파일하여 재사용 */
    private static final Pattern ALPHANUMERIC = Pattern.compile("^[A-Za-z0-9]+$");

    private final ExtensionPolicyRepository extensionPolicyRepository;

    public ExtensionPolicyService(ExtensionPolicyRepository extensionPolicyRepository) {
        this.extensionPolicyRepository = extensionPolicyRepository;
    }

    /** 고정 확장자 조회 */
    public List<ExtensionPolicy> getFixedExtensions() {
        return extensionPolicyRepository.findByTypeOrderByIdAsc(ExtensionType.FIXED);
    }

    /** 커스텀 확장자 조회 */
    public List<ExtensionPolicy> getCustomExtensions() {
        return extensionPolicyRepository.findByTypeOrderByIdAsc(ExtensionType.CUSTOM);
    }

    /**
     * 현재 차단 중인 확장자 전체 집합: 체크된 FIXED 행 + CUSTOM은 모든 행
     */
    public Set<String> getCurrentlyBlockedExtensions() {
        Set<String> blocked = new HashSet<>();
        extensionPolicyRepository.findByTypeOrderByIdAsc(ExtensionType.FIXED).stream()
                .filter(ExtensionPolicy::isBlocked)
                .map(ExtensionPolicy::getExtension)
                .forEach(blocked::add);
        extensionPolicyRepository.findByTypeOrderByIdAsc(ExtensionType.CUSTOM).stream()
                .map(ExtensionPolicy::getExtension)
                .forEach(blocked::add);
        return blocked;
    }

    /**
     * 고정 확장자 설정(체크)
     */
    @Transactional
    public ExtensionPolicy setFixedBlocked(String extension, boolean blocked) {
        ExtensionPolicy fixed = extensionPolicyRepository.findByTypeAndExtensionIgnoreCase(ExtensionType.FIXED, extension)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 고정 확장자입니다"));
        fixed.setBlocked(blocked);
        return extensionPolicyRepository.save(fixed);
    }

    /**
     * {@code rawInput}을 검증 순서대로 통과시킨 뒤 커스텀 확장자로 저장한다.
     *
     * <ol>
     *   <li>{@link #normalize}로 공백 제거 및 영문/숫자 이외 문자를 걸러낸다.</li>
     *   <li>고정 확장자와 겹치는지, 이미 등록된 커스텀 확장자인지 검사한다.</li>
     *   <li>커스텀 확장자가 200개 상한을 넘었는지 검사한다.</li>
     * </ol>
     */
    @Transactional
    public ExtensionPolicy addCustomExtension(String rawInput) {
        String normalized = normalize(rawInput);

        if (extensionPolicyRepository.existsByTypeAndExtensionIgnoreCase(ExtensionType.FIXED, normalized)) {
            throw new ExtensionValidationException("고정 확장자에 있는 확장자입니다");
        }
        if (extensionPolicyRepository.existsByTypeAndExtensionIgnoreCase(ExtensionType.CUSTOM, normalized)) {
            throw new ExtensionValidationException("이미 등록된 확장자입니다");
        }
        if (extensionPolicyRepository.countByType(ExtensionType.CUSTOM) >= CUSTOM_EXTENSION_MAX_COUNT) {
            throw new ExtensionValidationException("최대 200개까지 등록할 수 있습니다");
        }

        try {
            return extensionPolicyRepository.save(new ExtensionPolicy(normalized, ExtensionType.CUSTOM, true));
        } catch (DataIntegrityViolationException raceLostToConcurrentInsert) {
            // 이 catch 자체는 경쟁 상태를 막지 않음
            // 대신 DB 유니크 인덱스가 막음
            throw new ExtensionValidationException("이미 등록된 확장자입니다");
        }
    }

    @Transactional
    public void deleteCustomExtension(Long id) {
        ExtensionPolicy custom = extensionPolicyRepository.findByIdAndType(id, ExtensionType.CUSTOM)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 커스텀 확장자입니다"));
        extensionPolicyRepository.delete(custom);
    }

    /**
     * 공백을 trim하고 빈 값/길이초과/영문+숫자 이외 문자를 거부하고 소문자로 변환
     * 결과적으로 저장되는 모든 확장자와 모든 비교가 구조적으로 대소문자 무시
     */
    private String normalize(String rawInput) {
        String trimmed = rawInput == null ? "" : rawInput.trim();
        if (trimmed.isEmpty()
                || trimmed.length() > CUSTOM_EXTENSION_MAX_LENGTH
                || !ALPHANUMERIC.matcher(trimmed).matches()) {
            throw new InvalidExtensionFormatException("영문/숫자만 입력할 수 있습니다");
        }
        return trimmed.toLowerCase();
    }
}
