package com.voiceassistant.service;

import com.voiceassistant.exception.FormScanOcrException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TesseractImageOcrService implements ImageOcrService {

    private final String command;
    private final String languages;
    private final int pageSegmentationMode;
    private final long timeoutSeconds;

    public TesseractImageOcrService(
            @Value("${app.form-scan.ocr.command:tesseract}") String command,
            @Value("${app.form-scan.ocr.languages:swe+eng}") String languages,
            @Value("${app.form-scan.ocr.page-segmentation-mode:6}") int pageSegmentationMode,
            @Value("${app.form-scan.ocr.timeout-seconds:60}") long timeoutSeconds) {
        this.command = command;
        this.languages = languages;
        this.pageSegmentationMode = pageSegmentationMode;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String extractText(Path imagePath, String contentType) {
        Process process;
        try {
            process = new ProcessBuilder(
                    List.of(command, imagePath.toString(), "stdout", "-l", languages, "--psm", String.valueOf(pageSegmentationMode)))
                    .start();
        } catch (IOException e) {
            throw new FormScanOcrException("Tesseract OCR is unavailable on the server", e);
        }

        try {
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new FormScanOcrException("Image OCR timed out. Try a smaller or sharper image.");
            }

            String extractedText = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                log.warn("Tesseract failed exitCode={} errorLength={}", process.exitValue(), errorOutput.length());
                throw new FormScanOcrException("Could not read this image. Try a sharper, brighter photo.");
            }
            if (extractedText.isBlank()) {
                throw new FormScanOcrException("No readable text was found. Try a sharper, brighter photo.");
            }
            return extractedText;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new FormScanOcrException("Image OCR was interrupted", e);
        } catch (IOException e) {
            throw new FormScanOcrException("Could not read Tesseract OCR output", e);
        }
    }
}
