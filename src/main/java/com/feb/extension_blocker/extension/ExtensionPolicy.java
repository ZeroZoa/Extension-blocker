package com.feb.extension_blocker.extension;

import com.feb.extension_blocker.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

/**
 * 확장자 차단 정책 한 건을 나타내는 엔티티.
 *
 * <p>고정 확장자({@code type == FIXED})는 {@code data.sql}로 시드되고 관리자가
 * {@code blocked} 플래그만 토글한다.
 * 커스텀 확장자({@code type == CUSTOM})는 존재 자체가 차단을 의미해,
 * 해제 시 플래그가 아니라 행 자체를 삭제한다.
 *
 * <p>{@code extension} 컬럼은 대소문자를 무시한 유니크 인덱스를 가진다({@code schema.sql}
 * 참고) -> 고정/커스텀 충돌을 DB 레벨에서 막기 위함이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "extension_policy")
public class ExtensionPolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String extension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExtensionType type;

    @Setter
    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    public ExtensionPolicy(@NonNull String extension, @NonNull ExtensionType type, boolean blocked) {
        this.extension = extension;
        this.type = type;
        this.blocked = blocked;
    }
}
