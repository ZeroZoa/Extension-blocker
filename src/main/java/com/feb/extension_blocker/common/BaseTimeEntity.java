package com.feb.extension_blocker.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 서브클래스 엔티티에 {@code created_at}/{@code updated_at} 컬럼을 추가한다.
 *
 * <p>{@link java.time.LocalDateTime} 대신 {@link Instant}를 쓴다 — 로컬(KST)과 배포
 * 서버(UTC)가 같은 테이블에 써도 값이 항상 동일한 시점을 가리키도록 하기 위해서다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
