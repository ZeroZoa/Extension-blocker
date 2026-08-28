package com.feb.extension_blocker.upload;

import com.feb.extension_blocker.upload.dto.UploadSuccessResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 업로드 엔드포인트. 요청당 파일 하나만 받는다({@code MultipartFile}이지
 * {@code List<MultipartFile>}이 아님) — 다중 파일 업로드는 과제 범위 밖이라 런타임
 * 검사가 아니라 API 시그니처 자체로 1개 제한을 강제한다.
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping("/upload")
    public UploadSuccessResponse upload(@RequestParam("file") MultipartFile file) {
        return fileUploadService.upload(file);
    }
}
