package com.feb.extension_blocker.upload;

import com.feb.extension_blocker.extension.ExtensionPolicyService;
import com.feb.extension_blocker.upload.dto.UploadSuccessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileUploadService} 파이프라인의 핵심 시나리오만 검증한다. DB는 실제로 붙이지
 * 않고 {@link ExtensionPolicyService}와 {@link UploadFileRepository}를 목(mock)으로
 * 대체한다 — 이 서비스가 책임지는 건 "정책을 어떻게 조회하는가"가 아니라 "조회된
 * 정책을 검증 파이프라인에 어떻게 적용하는가"이므로, 정책 자체의 정합성은
 * {@code ExtensionPolicyService}의 별도 테스트가 책임지는 게 맞다.
 */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    private static final byte[] PNG_HEADER =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final byte[] PE_HEADER = {0x4D, 0x5A, 0x00, 0x00};

    @Mock
    private ExtensionPolicyService extensionPolicyService;

    @Mock
    private UploadFileRepository uploadFileRepository;

    @TempDir
    private Path tempDir;

    private FileUploadService fileUploadService;

    @BeforeEach
    void setUp() {
        fileUploadService = new FileUploadService(extensionPolicyService, uploadFileRepository, tempDir.toString());
    }

    @Test
    @DisplayName("정상 파일은 저장되고 성공 응답을 반환")
    void uploadsValidFile() throws IOException {
        when(extensionPolicyService.getCurrentlyBlockedExtensions()).thenReturn(Set.of());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", PNG_HEADER);

        UploadSuccessResponse response = fileUploadService.upload(file);

        assertEquals("png", response.extension());
        assertEquals("photo.png", response.originalFilename());
        assertTrue(hasOneStoredFile(), "실제로 저장 디렉터리에 파일 하나가 물리적으로 저장되어야 한다");
        verify(uploadFileRepository).save(any());
    }

    @Test
    @DisplayName("차단 정책에 걸린 실제 확장자는 거부")
    void rejectsBlockedExtension() {
        when(extensionPolicyService.getCurrentlyBlockedExtensions()).thenReturn(Set.of("exe"));
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", PE_HEADER);

        UploadRejectedException e = assertThrows(UploadRejectedException.class, () -> fileUploadService.upload(file));

        assertEquals("차단된 확장자입니다: .exe", e.getMessage());
        assertNoFilesStored();
    }

    @Test
    @DisplayName("과제 핵심 사례: report.jpg인데 실제 내용이 실행파일이면 위장으로 거부")
    void rejectsDisguisedExecutable() {
        MockMultipartFile file = new MockMultipartFile("file", "report.jpg", "image/jpeg", PE_HEADER);

        UploadRejectedException e = assertThrows(UploadRejectedException.class, () -> fileUploadService.upload(file));

        assertEquals("파일의 실제 형식과 확장자가 일치하지 않습니다", e.getMessage());
        // 3단계(위장 검사)에서 이미 거부되어 4단계(정책 조회)까지 가지 않으므로
        // extensionPolicyService는 이 시나리오에서 아예 호출되지 않는다.
        assertNoFilesStored();
    }

    @Test
    @DisplayName("이중 확장자는 정책 조회 전에 거부")
    void rejectsDoubleExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf.exe", "application/pdf", PE_HEADER);

        UploadRejectedException e = assertThrows(UploadRejectedException.class, () -> fileUploadService.upload(file));

        assertTrue(e.getMessage().contains("이중 확장자"));
        assertNoFilesStored();
    }

    private boolean hasOneStoredFile() throws IOException {
        try (Stream<Path> files = Files.list(tempDir)) {
            return files.count() == 1;
        }
    }

    private void assertNoFilesStored() {
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(0, files.count(), "거부된 파일은 물리적으로 저장되어서는 안 된다");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
