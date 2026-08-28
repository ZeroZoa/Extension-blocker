package com.feb.extension_blocker.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 서브클래스 엔티티에 {@code created_at}/{@code updated_at} 감사(auditing) 컬럼을 추가한다.
 *
 * <p>{@link java.time.LocalDateTime} 대신 {@link Instant}를 쓰는 이유: 저장되는 타임스탬프는
 * "특정 시점"을 명확하게 나타내야 하며, 이를 기록한 서버가 어떤 타임존을 쓰는지에 좌우되는
 * 벽시계 값이어서는 안 된다. 로컬 개발 환경(KST)과 배포 환경(보통 UTC)이 같은 테이블에 값을
 * 쓸 때도 서로 비교 가능한 값이어야 하는데, {@code Instant}는 이를 보장하지만
 * {@code LocalDateTime}은 그렇지 않다.
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
