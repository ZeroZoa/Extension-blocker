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

    private static final int CUSTOM_EXTENSION_MAX_LENGTH = 20;
    private static final int CUSTOM_EXTENSION_MAX_COUNT = 200;
    private static final Pattern ALPHANUMERIC = Pattern.compile("^[A-Za-z0-9]+$");

    private final ExtensionPolicyRepository repository;

    public ExtensionPolicyService(ExtensionPolicyRepository repository) {
        this.repository = repository;
    }

    public List<ExtensionPolicy> getFixedExtensions() {
        return repository.findByTypeOrderByIdAsc(ExtensionType.FIXED);
    }

    public List<ExtensionPolicy> getCustomExtensions() {
        return repository.findByTypeOrderByIdAsc(ExtensionType.CUSTOM);
    }

    /**
     * 현재 차단 중인 확장자 전체 집합: 체크된 FIXED 행 ∪ 모든 CUSTOM 행
     * (CUSTOM은 존재하는 것 자체가 차단을 의미한다).
     */
    public Set<String> getCurrentlyBlockedExtensions() {
        Set<String> blocked = new HashSet<>();
        repository.findByType(ExtensionType.FIXED).stream()
                .filter(ExtensionPolicy::isBlocked)
                .map(ExtensionPolicy::getExtension)
                .forEach(blocked::add);
        repository.findByType(ExtensionType.CUSTOM).stream()
                .map(ExtensionPolicy::getExtension)
                .forEach(blocked::add);
        return blocked;
    }

    @Transactional
    public ExtensionPolicy setFixedBlocked(String extension, boolean blocked) {
        ExtensionPolicy fixed = repository.findByTypeAndExtensionIgnoreCase(ExtensionType.FIXED, extension)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 고정 확장자입니다"));
        fixed.setBlocked(blocked);
        return repository.save(fixed);
    }

    /**
     * {@code rawInput}을 정규화한 뒤 고정/커스텀 충돌 여부와 200개 상한을 검사하고 저장한다.
     *
     * <p>위 검사들은 insert 이전에 이루어지는 일반적인 읽기 연산이라서, 같은 확장자를
     * 추가하는 두 요청이 동시에 들어오면 둘 다 커밋 전에 모든 검사를 통과해버릴 수 있다 —
     * 이건 가정이 아니라 실제로 발생 가능한 경쟁 상태(race condition)다. 이를 실질적으로
     * 막아주는 건 DB의 대소문자 무시 유니크 인덱스이고, 아래의
     * {@link DataIntegrityViolationException} catch 구문은 그 DB 레벨 거부를 사전 검사와
     * 동일한 사용자 메시지로 변환해줄 뿐이다.
     */
    @Transactional
    public ExtensionPolicy addCustomExtension(String rawInput) {
        String normalized = normalize(rawInput);

        if (repository.existsByTypeAndExtensionIgnoreCase(ExtensionType.FIXED, normalized)) {
            throw new ExtensionValidationException("고정 확장자에 있는 확장자입니다");
        }
        if (repository.existsByTypeAndExtensionIgnoreCase(ExtensionType.CUSTOM, normalized)) {
            throw new ExtensionValidationException("이미 등록된 확장자입니다");
        }
        if (repository.countByType(ExtensionType.CUSTOM) >= CUSTOM_EXTENSION_MAX_COUNT) {
            throw new ExtensionValidationException("최대 200개까지 등록할 수 있습니다");
        }

        try {
            return repository.save(new ExtensionPolicy(normalized, ExtensionType.CUSTOM, true));
        } catch (DataIntegrityViolationException raceLostToConcurrentInsert) {
            throw new ExtensionValidationException("이미 등록된 확장자입니다");
        }
    }

    @Transactional
    public void deleteCustomExtension(Long id) {
        ExtensionPolicy custom = repository.findByIdAndType(id, ExtensionType.CUSTOM)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 커스텀 확장자입니다"));
        repository.delete(custom);
    }

    /**
     * 공백을 trim하고 빈 값/길이초과/영문+숫자 이외 문자를 거부한 뒤 소문자로 변환한다.
     * 이렇게 하면 저장되는 모든 확장자와 모든 비교가 "관례상"이 아니라 "구조적으로"
     * 대소문자 무시가 된다.
     */
    private String normalize(String rawInput) {
        String trimmed = rawInput == null ? "" : rawInput.trim();
        if (trimmed.isEmpty()
                || trimmed.length() > CUSTOM_EXTENSION_MAX_LENGTH
                || !ALPHANUMERIC.matcher(trimmed).matches()) {
            throw new ExtensionValidationException("영문/숫자만 입력할 수 있습니다");
        }
        return trimmed.toLowerCase();
    }
}
