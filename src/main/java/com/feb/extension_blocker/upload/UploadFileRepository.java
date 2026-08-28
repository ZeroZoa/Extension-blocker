package com.feb.extension_blocker.upload;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link UploadFile} 이력에 대한 데이터 접근. 지금은 저장만 하고 별도 조회 메서드는
 * 없다 — 업로드 이력 조회 화면/API가 과제 범위 밖이기 때문이다(9장 Out of Scope와
 * 같은 판단).
 */
public interface UploadFileRepository extends JpaRepository<UploadFile, Long> {
}
