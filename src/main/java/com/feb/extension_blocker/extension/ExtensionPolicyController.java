package com.feb.extension_blocker.extension;

import com.feb.extension_blocker.extension.dto.CustomExtensionRequest;
import com.feb.extension_blocker.extension.dto.CustomExtensionResponse;
import com.feb.extension_blocker.extension.dto.FixedExtensionResponse;
import com.feb.extension_blocker.extension.dto.PatchFixedRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 확장자 차단 정책을 관리하는 REST 엔드포인트.
 * 여기서의 모든 쓰기 작업은 즉시 반영된다 — 별도의 저장/적용 단계가 없으며,
 * 관리 화면에서 "체크/추가하면 곧 저장된 것"이라는 동작과 그대로 대응한다.
 */
@RestController
@RequestMapping("/api/extensions")
public class ExtensionPolicyController {

    private final ExtensionPolicyService extensionPolicyService;

    public ExtensionPolicyController(ExtensionPolicyService extensionPolicyService) {
        this.extensionPolicyService = extensionPolicyService;
    }

    @GetMapping("/fixed")
    public List<FixedExtensionResponse> getFixedExtensions() {
        return extensionPolicyService.getFixedExtensions().stream()
                .map(FixedExtensionResponse::from)
                .toList();
    }

    @PatchMapping("/fixed/{extension}")
    public FixedExtensionResponse patchFixedExtension(@PathVariable String extension,
                                                        @Valid @RequestBody PatchFixedRequest request) {
        ExtensionPolicy updated = extensionPolicyService.setFixedBlocked(extension, request.blocked());
        return FixedExtensionResponse.from(updated);
    }

    @GetMapping("/custom")
    public List<CustomExtensionResponse> getCustomExtensions() {
        return extensionPolicyService.getCustomExtensions().stream()
                .map(CustomExtensionResponse::from)
                .toList();
    }

    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomExtensionResponse addCustomExtension(@Valid @RequestBody CustomExtensionRequest request) {
        ExtensionPolicy created = extensionPolicyService.addCustomExtension(request.extension());
        return CustomExtensionResponse.from(created);
    }

    @DeleteMapping("/custom/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomExtension(@PathVariable Long id) {
        extensionPolicyService.deleteCustomExtension(id);
    }
}
