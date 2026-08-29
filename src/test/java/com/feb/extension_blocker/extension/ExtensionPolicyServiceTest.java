package com.feb.extension_blocker.extension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DB는 실제로 붙이지 않고 {@link ExtensionPolicyRepository}를 목(mock)으로 대체한다.
 * 특히 {@link #addCustomExtension}의 검사 순서(형식 -> 고정 충돌 -> 커스텀 중복 -> 상한)와
 * {@code normalize()}의 경계값을 집중적으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ExtensionPolicyServiceTest {

    @Mock
    private ExtensionPolicyRepository repository;

    private ExtensionPolicyService service;

    @BeforeEach
    void setUp() {
        service = new ExtensionPolicyService(repository);
    }

    @Test
    @DisplayName("체크된 FIXED와 모든 CUSTOM의 합집합을 반환한다")
    void currentlyBlockedExtensionsIsUnionOfCheckedFixedAndAllCustom() {
        ExtensionPolicy checkedExe = new ExtensionPolicy("exe", ExtensionType.FIXED, true);
        ExtensionPolicy uncheckedJs = new ExtensionPolicy("js", ExtensionType.FIXED, false);
        ExtensionPolicy customSh = new ExtensionPolicy("sh", ExtensionType.CUSTOM, true);
        when(repository.findByTypeOrderByIdAsc(ExtensionType.FIXED)).thenReturn(List.of(checkedExe, uncheckedJs));
        when(repository.findByTypeOrderByIdAsc(ExtensionType.CUSTOM)).thenReturn(List.of(customSh));

        Set<String> blocked = service.getCurrentlyBlockedExtensions();

        assertEquals(Set.of("exe", "sh"), blocked, "체크 안 된 js는 빠지고, custom은 무조건 포함되어야 한다");
    }

    @Test
    @DisplayName("존재하는 고정 확장자는 blocked 플래그를 토글하고 저장한다")
    void setFixedBlockedTogglesExistingExtension() {
        ExtensionPolicy exe = new ExtensionPolicy("exe", ExtensionType.FIXED, false);
        when(repository.findByTypeAndExtensionIgnoreCase(ExtensionType.FIXED, "exe")).thenReturn(Optional.of(exe));
        when(repository.save(exe)).thenReturn(exe);

        ExtensionPolicy result = service.setFixedBlocked("exe", true);

        assertTrue(result.isBlocked());
    }

    @Test
    @DisplayName("존재하지 않는 고정 확장자는 404로 거부한다")
    void setFixedBlockedRejectsUnknownExtension() {
        when(repository.findByTypeAndExtensionIgnoreCase(ExtensionType.FIXED, "zzz")).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.setFixedBlocked("zzz", true));

        assertEquals(404, e.getStatusCode().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "sh!", "sh sh", "확장자"})
    @DisplayName("빈 값/공백뿐/특수문자/공백포함/비영문숫자는 형식 오류로 거부한다")
    void rejectsInvalidFormat(String input) {
        assertThrows(InvalidExtensionFormatException.class, () -> service.addCustomExtension(input));
    }

    @Test
    @DisplayName("21자는 길이초과로 거부한다")
    void rejectsTooLong() {
        assertThrows(InvalidExtensionFormatException.class, () -> service.addCustomExtension("a".repeat(21)));
    }

    @Test
    @DisplayName("정확히 20자는 경계값으로 통과한다")
    void acceptsExactlyMaxLength() {
        stubNoConflictsAndRoomLeft();

        assertDoesNotThrow(() -> service.addCustomExtension("a".repeat(20)));
    }

    @Test
    @DisplayName("앞뒤 공백은 trim되고 대문자는 소문자로 정규화된다")
    void trimsAndLowercases() {
        stubNoConflictsAndRoomLeft();

        ExtensionPolicy result = service.addCustomExtension("  SH  ");

        assertEquals("sh", result.getExtension());
    }

    @Test
    @DisplayName("고정 확장자와 겹치면 거부하고 저장을 시도하지 않는다")
    void rejectsWhenConflictsWithFixed() {
        when(repository.existsByTypeAndExtensionIgnoreCase(ExtensionType.FIXED, "exe")).thenReturn(true);

        ExtensionValidationException e = assertThrows(ExtensionValidationException.class,
                () -> service.addCustomExtension("exe"));

        assertEquals("고정 확장자에 있는 확장자입니다", e.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("이미 등록된 커스텀 확장자는 거부한다")
    void rejectsDuplicateCustom() {
        when(repository.existsByTypeAndExtensionIgnoreCase(ExtensionType.FIXED, "sh")).thenReturn(false);
        when(repository.existsByTypeAndExtensionIgnoreCase(ExtensionType.CUSTOM, "sh")).thenReturn(true);

        ExtensionValidationException e = assertThrows(ExtensionValidationException.class,
                () -> service.addCustomExtension("sh"));

        assertEquals("이미 등록된 확장자입니다", e.getMessage());
    }

    @Test
    @DisplayName("200개 상한에 도달하면 거부한다")
    void rejectsWhenAtCap() {
        when(repository.existsByTypeAndExtensionIgnoreCase(ExtensionType.FIXED, "new")).thenReturn(false);
        when(repository.existsByTypeAndExtensionIgnoreCase(ExtensionType.CUSTOM, "new")).thenReturn(false);
        when(repository.countByType(ExtensionType.CUSTOM)).thenReturn(200L);

        ExtensionValidationException e = assertThrows(ExtensionValidationException.class,
                () -> service.addCustomExtension("new"));

        assertEquals("최대 200개까지 등록할 수 있습니다", e.getMessage());
    }

    @Test
    @DisplayName("중복과 상한초과가 동시에 해당하면 더 구체적인 중복 메시지를 우선한다(검사 순서 검증)")
    void duplicateCheckTakesPriorityOverCapCheck() {
        when(repository.existsByTypeAndExtensionIgnoreCase(ExtensionType.FIXED, "sh")).thenReturn(false);
        when(repository.existsByTypeAndExtensionIgnoreCase(ExtensionType.CUSTOM, "sh")).thenReturn(true);
        // countByType은 의도적으로 스텁하지 않는다 — 중복 검사에서 먼저 걸려 도달하면 안 된다.

        ExtensionValidationException e = assertThrows(ExtensionValidationException.class,
                () -> service.addCustomExtension("sh"));

        assertEquals("이미 등록된 확장자입니다", e.getMessage());
        verify(repository, never()).countByType(any());
    }

    @Test
    @DisplayName("사전 검사를 통과해도 동시 삽입으로 DB 유니크 제약에 걸리면 같은 중복 메시지로 변환한다")
    void translatesRaceConditionToDuplicateMessage() {
        stubNoConflictsAndRoomLeft();
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("unique violation"));

        ExtensionValidationException e = assertThrows(ExtensionValidationException.class,
                () -> service.addCustomExtension("sh"));

        assertEquals("이미 등록된 확장자입니다", e.getMessage());
    }

    @Test
    @DisplayName("존재하는 커스텀 확장자는 삭제한다")
    void deletesExistingCustomExtension() {
        ExtensionPolicy sh = new ExtensionPolicy("sh", ExtensionType.CUSTOM, true);
        when(repository.findByIdAndType(1L, ExtensionType.CUSTOM)).thenReturn(Optional.of(sh));

        service.deleteCustomExtension(1L);

        verify(repository).delete(sh);
    }

    @Test
    @DisplayName("존재하지 않는(또는 FIXED인) id는 404로 거부하고 삭제하지 않는다")
    void deleteRejectsUnknownOrFixedId() {
        when(repository.findByIdAndType(99L, ExtensionType.CUSTOM)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.deleteCustomExtension(99L));
        verify(repository, never()).delete(any());
    }

    private void stubNoConflictsAndRoomLeft() {
        when(repository.existsByTypeAndExtensionIgnoreCase(eq(ExtensionType.FIXED), any())).thenReturn(false);
        when(repository.existsByTypeAndExtensionIgnoreCase(eq(ExtensionType.CUSTOM), any())).thenReturn(false);
        when(repository.countByType(ExtensionType.CUSTOM)).thenReturn(0L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
