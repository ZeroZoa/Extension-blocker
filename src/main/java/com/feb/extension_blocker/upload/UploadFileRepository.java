package com.feb.extension_blocker.upload;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link UploadFile} 이력에 대한 데이터 접근.
 * 현재 과제에서는 저장만 하고 별도 조회 메서드는 만들지 않음 -> 과제 범위 밖이라고 판단
 */
public interface UploadFileRepository extends JpaRepository<UploadFile, Long> {
}
