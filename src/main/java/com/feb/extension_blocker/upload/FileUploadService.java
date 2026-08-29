package com.feb.extension_blocker.upload;

import com.feb.extension_blocker.extension.ExtensionPolicyService;
import com.feb.extension_blocker.upload.dto.UploadSuccessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 업로드된 파일을 검증 순서대로 검사하고 저장
 *
 * <p>검증은 하나라도 실패하면 그 즉시 거부하고 이후 단계는 수행하지 않음
 * 5단계(특수 파일명 치환)는 1~4단계를 전부 통과한 파일에만 적용
 * 결과적으로 이름을 바꾼다고 차단 정책을 우회할 수 없도록 함
 *
 * <p>물리 저장(파일시스템 write)과 이력 저장(DB insert)은 하나의 트랜잭션으로 묶여있지
 * 않음 — file write는 성공했는데 DB insert가 실패하면 디스크엔 파일이 남고 이력엔
 * 기록이 안 남는 불일치가 이론적으로 가능함. 해결하려면 아웃박스 패턴 같은 분산
 * 트랜잭션 처리가 필요하지만 과제 규모와 기간 대비 과한 설계라 판단
 */
@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);
    private static final char NULL_BYTE = (char) 0;

    private final ExtensionPolicyService extensionPolicyService;
    private final UploadFileRepository uploadFileRepository;
    private final Path storageDir;

    public FileUploadService(ExtensionPolicyService extensionPolicyService,
                              UploadFileRepository uploadFileRepository,
                              @Value("${app.upload.dir}") String uploadDir) {
        this.extensionPolicyService = extensionPolicyService;
        this.uploadFileRepository = uploadFileRepository;
        this.storageDir = Path.of(uploadDir);
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public UploadSuccessResponse upload(MultipartFile file) {
        String rawFilename = file.getOriginalFilename();

        // null byte 포함 파일명(과거 "shell.php\0.jpg"류 우회 기법)은 즉시 거부
        // -> 원본 그대로 로그에 남기면 Postgres가 null byte를 저장 못 해 예외가 나서 여기서 미리 이스케이프
        if (rawFilename != null && rawFilename.indexOf(NULL_BYTE) >= 0) {
            reject(rawFilename.replace("\0", "\\0"), null, "허용되지 않는 파일명입니다");
        }

        FilenameAnalyzer analyzer = FilenameAnalyzer.analyze(rawFilename);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // 1단계: 콘텐츠만으로 실제 형식 판별 — 클라이언트가 보낸 MIME 타입은 참고하지 않는다
        String realExtension = FileSignatureDetector.detect(content, analyzer.claimedExtension());

        // 2단계: 이중 확장자
        if (analyzer.isDoubleExtension()) {
            reject(analyzer.basename(), realExtension, "허용되지 않는 파일명입니다 (이중 확장자)");
        }

        // 3단계: 확장자 위장 — 애초에 확장자를 주장하지 않은 파일은 비교 대상이 없어 건너뛴다
        if (analyzer.hasExtension() && !analyzer.claimedExtension().equals(realExtension)) {
            reject(analyzer.basename(), realExtension, "파일의 실제 형식과 확장자가 일치하지 않습니다");
        }

        // 4단계: 현재 차단 정책을 매 요청마다 새로 조회(캐시 금지)해 실제 확장자 기준으로 검사
        Set<String> blockedExtensions = extensionPolicyService.getCurrentlyBlockedExtensions();
        if (blockedExtensions.contains(realExtension)) {
            reject(analyzer.basename(), realExtension, "차단된 확장자입니다: ." + realExtension);
        }

        // 5단계: 특수 파일명 치환 — 1~4단계를 모두 통과한 파일에만 적용된다
        String logicalStoredName = analyzer.needsSpecialNaming()
                ? Instant.now().getEpochSecond() + "." + realExtension
                : analyzer.basename();

        // 실제 물리 저장 파일명은 항상 UUID다. 사용자 입력은 파일시스템 경로 결정에
        // 절대 관여하지 않으므로 경로 조작(디렉터리 traversal)이 원천적으로 불가능하다.
        String physicalName = UUID.randomUUID() + "." + realExtension;
        Path physicalPath = storageDir.resolve(physicalName);
        try {
            Files.write(physicalPath, content);
            restrictPermissionsIfSupported(physicalPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        log.info("파일 업로드 성공: original={}, stored={}, realExtension={}",
                analyzer.basename(), physicalName, realExtension);
        uploadFileRepository.save(new UploadFile(
                analyzer.basename(), logicalStoredName, realExtension, UploadStatus.SUCCESS, null));

        return new UploadSuccessResponse(logicalStoredName, analyzer.basename(), realExtension);
    }

    private void reject(String originalFilename, String realExtension, String reason) {
        log.info("파일 업로드 거부: original={}, realExtension={}, reason={}",
                originalFilename, realExtension, reason);
        uploadFileRepository.save(new UploadFile(
                originalFilename, null, realExtension, UploadStatus.REJECTED, reason));
        throw new UploadRejectedException(reason);
    }

    /** 저장 경로가 실수로 노출되는 경우에 대비해 파일의 실행 권한을 미리 제거한다. */
    private void restrictPermissionsIfSupported(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r-----"));
        } catch (UnsupportedOperationException notPosix) {
            // POSIX가 아닌 파일시스템(Windows 등) — 애초에 제거할 실행 비트가 없다.
        }
    }
}
