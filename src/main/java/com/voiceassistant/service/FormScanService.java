package com.voiceassistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceassistant.dto.FormScanApprovalRequestDTO;
import com.voiceassistant.dto.FormScanApprovalResponseDTO;
import com.voiceassistant.dto.FormScanResponseDTO;
import com.voiceassistant.dto.SourceType;
import com.voiceassistant.dto.TextAnalysisApprovalRequestDTO;
import com.voiceassistant.dto.TextAnalysisApprovalResponseDTO;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.exception.FormScanException;
import com.voiceassistant.model.AppUser;
import com.voiceassistant.model.FormScan;
import com.voiceassistant.model.FormScanStatus;
import com.voiceassistant.repository.FormScanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Slf4j
@Service
public class FormScanService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ImageOcrService imageOcrService;
    private final TextAnalysisService textAnalysisService;
    private final FormScanInterpreter interpreter;
    private final AppUserService appUserService;
    private final FormScanRepository formScanRepository;
    private final ObjectMapper objectMapper;
    private final long maxBytes;

    public FormScanService(
            ImageOcrService imageOcrService,
            TextAnalysisService textAnalysisService,
            FormScanInterpreter interpreter,
            AppUserService appUserService,
            FormScanRepository formScanRepository,
            ObjectMapper objectMapper,
            @Value("${app.form-scan.max-bytes:10485760}") long maxBytes) {
        this.imageOcrService = imageOcrService;
        this.textAnalysisService = textAnalysisService;
        this.interpreter = interpreter;
        this.appUserService = appUserService;
        this.formScanRepository = formScanRepository;
        this.objectMapper = objectMapper;
        this.maxBytes = maxBytes;
    }

    public FormScanResponseDTO scan(MultipartFile file) {
        validate(file);
        AppUser owner = appUserService.getCurrentUser();
        Path temporaryImage = null;

        try {
            temporaryImage = writeTemporaryImage(file);
            String extractedText = imageOcrService.extractText(temporaryImage, file.getContentType());
            TextAnalysisResponseDTO analysis = textAnalysisService.analyze(new TextAnalysisRequestDTO(
                    "Scanned paper form",
                    extractedText,
                    SourceType.FORM_SCAN,
                    OffsetDateTime.now(TextAnalysisService.DEFAULT_ZONE),
                    TextAnalysisService.DEFAULT_ZONE.getId()));
            FormScanInterpreter.Interpretation interpretation = interpreter.interpret(extractedText, analysis);

            FormScan formScan = new FormScan();
            formScan.setOwner(owner);
            formScan.setCreatedAt(OffsetDateTime.now().toInstant());
            formScan.setStatus(FormScanStatus.READY_FOR_REVIEW);
            formScan.setDetectedFormType(interpretation.detectedFormType());
            formScan.setChildName(interpretation.childName());
            formScan.setSummary(analysis.summary());
            formScan.setExtractedText(extractedText);
            formScan.setDraftJson(serializeDraft(analysis));
            formScan.setConfidence(interpretation.confidence());
            FormScan saved = formScanRepository.save(formScan);

            log.info("Completed form scan scanId={} ownerId={} type={} chars={}",
                    saved.getId(), owner.getId(), saved.getDetectedFormType(), extractedText.length());
            return toResponse(saved, analysis, interpretation.warnings());
        } catch (IOException e) {
            throw new FormScanException("Could not store the uploaded image temporarily", e);
        } finally {
            deleteTemporaryImage(temporaryImage);
        }
    }

    @Transactional
    public FormScanApprovalResponseDTO approve(Long scanId, FormScanApprovalRequestDTO request) {
        AppUser owner = appUserService.getCurrentUser();
        FormScan scan = formScanRepository.findByIdAndOwnerId(scanId, owner.getId())
                .orElseThrow(() -> new NoSuchElementException("Form scan not found"));
        if (scan.getStatus() == FormScanStatus.APPROVED) {
            throw new FormScanException("This form scan has already been approved");
        }
        List<?> events = request.events() == null ? List.of() : request.events();
        List<?> todos = request.todos() == null ? List.of() : request.todos();
        if (events.isEmpty() && todos.isEmpty()) {
            throw new FormScanException("Select at least one todo or calendar event before approving");
        }

        TextAnalysisApprovalResponseDTO approval = textAnalysisService.approve(new TextAnalysisApprovalRequestDTO(
                request.events(), request.todos()));
        scan.setStatus(FormScanStatus.APPROVED);
        scan.setApprovedAt(OffsetDateTime.now().toInstant());
        formScanRepository.save(scan);

        return new FormScanApprovalResponseDTO(scan.getId(), scan.getStatus(), approval, approval.warnings());
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FormScanException("Choose a non-empty image to scan");
        }
        if (file.getSize() > maxBytes) {
            throw new FormScanException("The image is too large. Maximum size is " + (maxBytes / (1024 * 1024)) + " MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new FormScanException("Unsupported image type. Use JPEG, PNG or WebP");
        }
        if (!hasExpectedImageSignature(file, contentType)) {
            throw new FormScanException("The uploaded file does not match its declared image type");
        }
    }

    private boolean hasExpectedImageSignature(MultipartFile file, String contentType) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            return switch (contentType.toLowerCase()) {
                case "image/jpeg" -> header.length >= 3
                        && (header[0] & 0xFF) == 0xFF
                        && (header[1] & 0xFF) == 0xD8
                        && (header[2] & 0xFF) == 0xFF;
                case "image/png" -> header.length >= 8
                        && (header[0] & 0xFF) == 0x89
                        && header[1] == 'P'
                        && header[2] == 'N'
                        && header[3] == 'G'
                        && (header[4] & 0xFF) == 0x0D
                        && (header[5] & 0xFF) == 0x0A
                        && (header[6] & 0xFF) == 0x1A
                        && (header[7] & 0xFF) == 0x0A;
                case "image/webp" -> header.length >= 12
                        && "RIFF".equals(new String(header, 0, 4, StandardCharsets.US_ASCII))
                        && "WEBP".equals(new String(header, 8, 4, StandardCharsets.US_ASCII));
                default -> false;
            };
        } catch (IOException e) {
            throw new FormScanException("Could not validate the uploaded image", e);
        }
    }

    private Path writeTemporaryImage(MultipartFile file) throws IOException {
        String suffix = switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        Path temporaryImage = Files.createTempFile("voice-assistant-form-", suffix);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, temporaryImage, StandardCopyOption.REPLACE_EXISTING);
        }
        return temporaryImage;
    }

    private String serializeDraft(TextAnalysisResponseDTO analysis) {
        try {
            return objectMapper.writeValueAsString(analysis);
        } catch (JsonProcessingException e) {
            throw new FormScanException("Could not save the form scan draft", e);
        }
    }

    private FormScanResponseDTO toResponse(
            FormScan scan,
            TextAnalysisResponseDTO analysis,
            List<com.voiceassistant.dto.TextAnalysisWarningDTO> warnings) {
        return new FormScanResponseDTO(
                scan.getId(),
                scan.getStatus(),
                scan.getDetectedFormType(),
                scan.getChildName(),
                scan.getSummary(),
                scan.getExtractedText(),
                analysis.todos() == null ? List.of() : analysis.todos(),
                analysis.events() == null ? List.of() : analysis.events(),
                scan.getConfidence(),
                warnings);
    }

    private void deleteTemporaryImage(Path temporaryImage) {
        if (temporaryImage == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryImage);
        } catch (IOException e) {
            log.warn("Could not delete temporary form image {}", temporaryImage, e);
        }
    }
}
