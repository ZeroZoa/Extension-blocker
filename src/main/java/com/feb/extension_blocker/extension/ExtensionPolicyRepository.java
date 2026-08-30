package com.feb.extension_blocker.extension;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link ExtensionPolicy}에 대한 데이터 접근 계층.
 * 고정/커스텀 행은 같은 테이블을 쓰지만 관리 규칙이 완전히 다르기 때문에,
 * 이 인터페이스에 직접 선언한 커스텀 조회 메서드는 전부 {@link ExtensionType}으로 범위를 한정
 * (상속받은 {@code JpaRepository}의 기본 메서드는 예외).
 */
public interface ExtensionPolicyRepository extends JpaRepository<ExtensionPolicy, Long> {

    List<ExtensionPolicy> findByTypeOrderByIdAsc(ExtensionType type);

    Optional<ExtensionPolicy> findByTypeAndExtensionIgnoreCase(ExtensionType type, String extension);

    Optional<ExtensionPolicy> findByIdAndType(Long id, ExtensionType type);

    boolean existsByTypeAndExtensionIgnoreCase(ExtensionType type, String extension);

    long countByType(ExtensionType type);

    /**
     * PostgreSQL 트랜잭션 범위 advisory lock을 건다.
     * 같은 {@code key}로 이미 잠긴 상태라면 그 트랜잭션이 끝날 때까지 대기하고, 끝나면 자동으로 풀림
     * {@link ExtensionPolicyService#addCustomExtension}의 "개수 확인
     * -> insert" 구간을 사실상 하나의 원자적 구간으로 만들어, 커스텀 확장자가 200개를 초과하는 것을 방지
     */
    @Query(value = "SELECT pg_advisory_xact_lock(:key)", nativeQuery = true)
    void lockForCustomExtensionInsert(@Param("key") long key);
}
