package com.feb.extension_blocker.extension;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link ExtensionPolicy}에 대한 데이터 접근 계층.
 * 고정/커스텀 행은 같은 테이블을 쓰지만 관리 규칙이 완전히 다르기 때문에,
 * 모든 조회 메서드는 {@link ExtensionType}으로 범위를 한정한다.
 */
public interface ExtensionPolicyRepository extends JpaRepository<ExtensionPolicy, Long> {

    List<ExtensionPolicy> findByTypeOrderByIdAsc(ExtensionType type);

    List<ExtensionPolicy> findByType(ExtensionType type);

    Optional<ExtensionPolicy> findByTypeAndExtensionIgnoreCase(ExtensionType type, String extension);

    Optional<ExtensionPolicy> findByIdAndType(Long id, ExtensionType type);

    boolean existsByTypeAndExtensionIgnoreCase(ExtensionType type, String extension);

    long countByType(ExtensionType type);
}
