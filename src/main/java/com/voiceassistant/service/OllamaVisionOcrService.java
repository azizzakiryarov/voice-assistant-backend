package com.voiceassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.voiceassistant.exception.FormScanException;
import com.voiceassistant.exception.FormScanOcrException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class OllamaVisionOcrService implements ImageOcrService {

    private static final String OCR_PROMPT = """
            You perform OCR on a photographed paper form. Transcribe every readable word, date, time,
            checkbox state and table value exactly. Preserve headings and rows in plain text. Do not
            infer missing values. Mark unreadable handwriting as [unclear]. Return only the transcription.
            """;

    private final RestTemplate restTemplate;
    private final String visionBaseUrl;
    private final String visionModel;

    public OllamaVisionOcrService(
            RestTemplate restTemplate,
            @Value("${app.form-scan.vision.base-url}") String visionBaseUrl,
            @Value("${app.form-scan.vision.model}") String visionModel) {
        this.restTemplate = restTemplate;
        this.visionBaseUrl = trimTrailingSlash(visionBaseUrl);
        this.visionModel = visionModel;
    }

    @Override
    public String extractText(Path imagePath, String contentType) {
        try {
            String encodedImage = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
            Map<String, Object> message = Map.of(
                    "role", "user",
                    "content", OCR_PROMPT,
                    "images", List.of(encodedImage));
            Map<String, Object> request = Map.of(
                    "model", visionModel,
                    "stream", false,
                    "messages", List.of(message));

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    visionBaseUrl + "/api/chat",
                    request,
                    JsonNode.class);
            String text = response.getBody() == null ? null : response.getBody().path("message").path("content").asText(null);
            if (text == null || text.isBlank()) {
                throw new FormScanOcrException("The vision model could not read any text from this image. Try a sharper, brighter photo.");
            }
            return text.trim();
        } catch (FormScanException e) {
            throw e;
        } catch (IOException e) {
            throw new FormScanException("Could not read the uploaded image", e);
        } catch (RuntimeException e) {
            throw new FormScanOcrException("Image OCR is unavailable. Verify that the configured Ollama vision model is installed.", e);
        }
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
