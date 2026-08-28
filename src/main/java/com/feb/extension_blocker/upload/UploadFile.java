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

/**
 * 업로드 시도 한 건(성공/거부)의 이력.
 *
 * <p>같은 정보를 애플리케이션 로그(SLF4J)에도 남기지만, 로그는 검색·집계가 번거롭고
 * 배포 환경에 따라 보존 기간이 짧을 수 있다. 이 테이블은 "최근에 어떤 확장자가 자주
 * 차단됐는지"류의 질문에 SQL로 바로 답할 수 있게 해주는, 로그와는 목적이 다른
 * 운영 자산이다.
 *
 * <p>업로드 목록 조회·다운로드 API는 만들지 않는다 — 과제 범위에 없는 기능이라
 * 이 테이블은 순수하게 쓰기 전용(write-only) 이력으로만 쓰인다.
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

    public UploadFile(String originalFilename, String storedFilename, String detectedExtension,
                       UploadStatus status, String rejectReason) {
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.detectedExtension = detectedExtension;
        this.status = status;
        this.rejectReason = rejectReason;
    }
}
