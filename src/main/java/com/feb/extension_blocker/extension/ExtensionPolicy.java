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
import lombok.Setter;

/**
 * 확장자 차단 정책 한 건을 나타내는 엔티티.
 *
 * <p>고정 확장자({@code type == FIXED})는 {@code data.sql}로 최초 1회 시드되는, 잘 알려진
 * 위험 확장자 7종이며 관리자가 {@code blocked} 플래그만 토글한다. 커스텀 확장자
 * ({@code type == CUSTOM})는 필요할 때마다 생성되고 항상 차단 상태다 — 이 테이블에 행이
 * 존재한다는 사실 자체가 곧 차단이므로, 해제할 때도 플래그를 끄는 게 아니라 행을 삭제한다.
 *
 * <p>{@code extension} 컬럼은 DB 레벨에서 대소문자를 무시하는 유니크 인덱스를 갖는다
 * ({@code schema.sql} 참고). 그래서 커스텀 확장자가 기존 고정/커스텀 확장자와 충돌하는
 * 경우 — 동시 요청 상황을 포함해서 — 애플리케이션 레벨 검사뿐 아니라 DB 자체에서도
 * 거부된다.
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

    public ExtensionPolicy(String extension, ExtensionType type, boolean blocked) {
        this.extension = extension;
        this.type = type;
        this.blocked = blocked;
    }
}
