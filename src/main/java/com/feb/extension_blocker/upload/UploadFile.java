package com.feb.extension_blocker.upload;

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

/**
 * 업로드 시도 한 건(성공/거부)의 이력.
 * SLF4J 로그로도 남기지만, 검색·집계가 쉽도록 이 테이블을 따로 설계
 *
 * <p>업로드 목록 조회·다운로드 API는 만들지 않음 -> 과제 범위 밖이라고 판단
 * 이 테이블은 쓰기 전용 이력으로만 쓰임
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "upload_file")
public class UploadFile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "stored_filename", length = 255)
    private String storedFilename;

    @Column(name = "detected_extension", length = 20)
    private String detectedExtension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UploadStatus status;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    public UploadFile(@NonNull String originalFilename, String storedFilename, String detectedExtension,
                       @NonNull UploadStatus status, String rejectReason) {
        // null byte 방어 경로는 FilenameAnalyzer를 거치기 전 원본을 그대로 전달 -> 500자 초과를 방어
        this.originalFilename = truncate(originalFilename, 500);
        this.storedFilename = storedFilename;
        // 미지 바이너리는 claimed 확장자를 길이 제한 없이 반환 가능(FileSignatureDetector) -> 20자 초과를 방어
        this.detectedExtension = truncate(detectedExtension, 20);
        this.status = status;
        this.rejectReason = rejectReason;
    }

    private static String truncate(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
