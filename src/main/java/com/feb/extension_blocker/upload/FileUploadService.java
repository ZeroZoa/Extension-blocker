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
 * 업로드된 파일을 검증 순서대로 검사하고 저장한다.
 *
 * <p>검증은 하나라도 실패하면 그 즉시 거부하고 이후 단계는 수행하지 않는다. 특히
 * 5단계(특수 파일명 치환)는 1~4단계를 전부 통과한 파일에만 적용된다 — 즉 이름을
 * 바꾼다고 차단 정책을 우회할 수 없다.
 *
 * <p>물리 저장(파일시스템 write)과 이력 저장(DB insert)은 하나의 트랜잭션으로 묶여있지
 * 않다. 파일 write는 성공했는데 그 직후 DB insert가 실패하면, 디스크에는 파일이
 * 남고 이력에는 기록이 안 남는 불일치가 이론적으로 가능하다. 이걸 완전히 막으려면
 * 아웃박스 패턴 같은 분산 트랜잭션 처리가 필요한데, 과제 규모 대비 과한 설계라
 * 판단해 의도적으로 다루지 않았다.
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

        // 파일명에 null byte가 섞여있으면(과거 "shell.php\0.jpg" 류의 업로드 우회
        // 기법) 이후 어떤 이름/경로 처리도 하지 않고 즉시 거부한다. 이력에 원본
        // 파일명을 남기기 전에 null byte를 이스케이프하는데, PostgreSQL의 text 계열
        // 컬럼은 문자열 안에 null byte(0x00)를 저장할 수 없어 원본 그대로 insert하면
        // DB 예외가 나기 때문이다 — 보안 로그를 남기려던 코드가 그 자체로 또 다른
        // 예외를 던지는 건 본말전도라 여기서 미리 치환한다.
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

        // 1단계: 콘텐츠만으로 실제 형식 판별 — 클라이언트가 보낸 MIME 타입은 참고하지 않는다.
        String realExtension = FileSignatureDetector.detect(content, analyzer.claimedExtension());

        // 2단계: 이중 확장자.
        if (analyzer.isDoubleExtension()) {
            reject(analyzer.basename(), realExtension, "허용되지 않는 파일명입니다 (이중 확장자)");
        }

        // 3단계: 확장자 위장 — 애초에 확장자를 주장하지 않은 파일은 비교 대상이 없어 건너뛴다.
        if (analyzer.hasExtension() && !analyzer.claimedExtension().equals(realExtension)) {
            reject(analyzer.basename(), realExtension, "파일의 실제 형식과 확장자가 일치하지 않습니다");
        }

        // 4단계: 현재 차단 정책을 매 요청마다 새로 조회(캐시 금지)해 실제 확장자 기준으로 검사.
        Set<String> blockedExtensions = extensionPolicyService.getCurrentlyBlockedExtensions();
        if (blockedExtensions.contains(realExtension)) {
            reject(analyzer.basename(), realExtension, "차단된 확장자입니다: ." + realExtension);
        }

        // 5단계: 특수 파일명 치환 — 1~4단계를 모두 통과한 파일에만 적용된다.
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

    /**
     * 저장된 파일에서 실행 권한을 제거한다. 1차 방어선은 애초에 웹에서 서빙되지 않는
     * 경로({@code app.upload.dir}, 정적 리소스 경로 밖)에 저장하는 것이지만, 나중에
     * 설정 실수로 그 경로가 정적 리소스로 노출되더라도 파일 자체가 실행 불가능하도록
     * 방어를 한 겹 더 둔다.
     */
    private void restrictPermissionsIfSupported(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r-----"));
        } catch (UnsupportedOperationException notPosix) {
            // POSIX가 아닌 파일시스템(Windows 등) — 애초에 제거할 실행 비트가 없다.
        }
    }
}
