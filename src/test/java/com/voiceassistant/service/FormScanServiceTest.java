package com.voiceassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceassistant.dto.FormScanApprovalRequestDTO;
import com.voiceassistant.dto.TextAnalysisApprovalResponseDTO;
import com.voiceassistant.dto.TextAnalysisEventDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.dto.TextAnalysisTodoDTO;
import com.voiceassistant.dto.TextAnalysisWarningDTO;
import com.voiceassistant.exception.FormScanException;
import com.voiceassistant.model.AppUser;
import com.voiceassistant.model.FormScan;
import com.voiceassistant.model.FormScanStatus;
import com.voiceassistant.repository.FormScanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FormScanServiceTest {

    private ImageOcrService imageOcrService;
    private TextAnalysisService textAnalysisService;
    private AppUserService appUserService;
    private FormScanRepository formScanRepository;
    private FormScanService service;

    @BeforeEach
    void setUp() {
        imageOcrService = mock(ImageOcrService.class);
        textAnalysisService = mock(TextAnalysisService.class);
        appUserService = mock(AppUserService.class);
        formScanRepository = mock(FormScanRepository.class);
        service = new FormScanService(
                imageOcrService,
                textAnalysisService,
                new FormScanInterpreter(),
                appUserService,
                formScanRepository,
                new ObjectMapper(),
                10 * 1024 * 1024);
    }

    @Test
    void scanCreatesOwnedDraftAndDeletesTemporaryImage(@TempDir Path temporaryDirectory) throws Exception {
        AppUser owner = owner(7L);
        AtomicReference<Path> uploadedImage = new AtomicReference<>();
        when(appUserService.getCurrentUser()).thenReturn(owner);
        when(imageOcrService.extractText(any(Path.class), eq("image/jpeg"))).thenAnswer(invocation -> {
            Path path = invocation.getArgument(0);
            uploadedImage.set(path);
            assertThat(Files.exists(path)).isTrue();
            return "Förskola schema\nBarnets namn: Ada\n2026-08-18 08:00-15:00";
        });
        when(textAnalysisService.analyze(any())).thenReturn(analysis());
        when(formScanRepository.save(any(FormScan.class))).thenAnswer(invocation -> {
            FormScan scan = invocation.getArgument(0);
            scan.setId(42L);
            return scan;
        });

        var response = service.scan(new MockMultipartFile(
                "file", "schedule.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}));

        assertThat(response.scanId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(FormScanStatus.READY_FOR_REVIEW);
        assertThat(response.detectedFormType()).isEqualTo("daycare_schedule");
        assertThat(response.childName()).isEqualTo("Ada");
        assertThat(response.suggestedCalendarEvents()).hasSize(1);
        assertThat(Files.exists(uploadedImage.get())).isFalse();
        verify(textAnalysisService, never()).approve(any());
    }

    @Test
    void scanRejectsUnsupportedImagesBeforeCallingOcr() {
        assertThatThrownBy(() -> service.scan(new MockMultipartFile(
                "file", "form.pdf", "application/pdf", new byte[]{1})))
                .isInstanceOf(FormScanException.class)
                .hasMessageContaining("Unsupported image type");

        verifyNoInteractions(imageOcrService, textAnalysisService, formScanRepository);
    }

    @Test
    void scanRejectsFilesThatOnlyClaimToBeImages() {
        assertThatThrownBy(() -> service.scan(new MockMultipartFile(
                "file", "not-an-image.jpg", "image/jpeg", new byte[]{1, 2, 3, 4})))
                .isInstanceOf(FormScanException.class)
                .hasMessageContaining("does not match its declared image type");

        verifyNoInteractions(imageOcrService, textAnalysisService, formScanRepository);
    }

    @Test
    void approveCreatesItemsOnlyForOwnerAndMarksScanApproved() {
        AppUser owner = owner(7L);
        FormScan scan = new FormScan();
        scan.setId(42L);
        scan.setOwner(owner);
        scan.setStatus(FormScanStatus.READY_FOR_REVIEW);
        when(appUserService.getCurrentUser()).thenReturn(owner);
        when(formScanRepository.findByIdAndOwnerId(42L, 7L)).thenReturn(Optional.of(scan));
        when(textAnalysisService.approve(any())).thenReturn(new TextAnalysisApprovalResponseDTO(
                List.of(), List.of(), 0, 0, List.of("Google Tasks är inte ansluten. Uppgiften sparades bara lokalt.")));

        var response = service.approve(42L, new FormScanApprovalRequestDTO(List.of(), List.of(todo())));

        assertThat(response.status()).isEqualTo(FormScanStatus.APPROVED);
        assertThat(scan.getApprovedAt()).isNotNull();
        assertThat(response.warnings()).contains("Google Tasks är inte ansluten. Uppgiften sparades bara lokalt.");
        verify(textAnalysisService).approve(any());
        verify(formScanRepository).save(scan);
    }

    @Test
    void approveCannotAccessAnotherUsersScan() {
        when(appUserService.getCurrentUser()).thenReturn(owner(7L));
        when(formScanRepository.findByIdAndOwnerId(42L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(42L, new FormScanApprovalRequestDTO(List.of(), List.of(todo()))))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("Form scan not found");

        verify(textAnalysisService, never()).approve(any());
    }

    private TextAnalysisResponseDTO analysis() {
        return new TextAnalysisResponseDTO(
                "Dagisschema för Ada",
                "sv",
                List.of(new TextAnalysisEventDTO(
                        "Förskola: 08:00–15:00", "Från inskannat schema", "2026-08-18T08:00:00",
                        "2026-08-18T15:00:00", false, null, null, null, 0.9, true, "2026-08-18 08:00-15:00")),
                List.of(todo()),
                List.of(),
                List.of(new TextAnalysisWarningDTO("YEAR_INFERRED", "Kontrollera årtal", true)));
    }

    private TextAnalysisTodoDTO todo() {
        return new TextAnalysisTodoDTO(
                "Skicka in schema", "Kontrollera tiderna", "2026-08-15", null,
                null, null, null, 0.8, true, "Skicka in senast 15 augusti");
    }

    private AppUser owner(Long id) {
        AppUser owner = new AppUser();
        owner.setId(id);
        owner.setEmail("owner@example.com");
        owner.setGoogleSubject("subject-" + id);
        return owner;
    }
}
