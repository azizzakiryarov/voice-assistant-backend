package com.voiceassistant.service;

import com.voiceassistant.dto.TranscriptionResponseDTO;
import com.voiceassistant.exception.AudioTranslationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.voiceassistant.mapper.Mapper.extractTranslatedText;

@Service
public class TranscriptionService {

    private static final String WHISPER_PATH = "/v1/audio/transcriptions";
    private final String whisperBaseUrl;
    private final RestTemplate restTemplate;
    public TranscriptionService(RestTemplate restTemplate,
                                @Value("${transcription.whisper.base-url}") String whisperBaseUrl) {
        this.restTemplate = restTemplate;
        this.whisperBaseUrl = whisperBaseUrl;
    }

    // Regular expression för att hitta e-postadresser
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    public TranscriptionResponseDTO transcribeAudio(MultipartFile audioFile) {
        try {
            if (audioFile == null || audioFile.isEmpty()) {
                throw new AudioTranslationException("Audio file is missing or empty");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return Optional.ofNullable(audioFile.getOriginalFilename()).orElse("audio.wav");
                }
            });
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(whisperBaseUrl + WHISPER_PATH, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TranscriptionResponseDTO transcriptionResponseDTO = new TranscriptionResponseDTO();
                String translatedText = extractTranslatedText(response.getBody());
                String translatedTextWithCleanedEmailAddress = cleanEmailAddress(translatedText);
                transcriptionResponseDTO.setTranscription(translatedTextWithCleanedEmailAddress);
                String extractedEmailAddressForVerification = extractEmailAddress(translatedTextWithCleanedEmailAddress);
                transcriptionResponseDTO.setExtractedEmail(extractedEmailAddressForVerification);
                return transcriptionResponseDTO;
            } else {
                throw new AudioTranslationException("Failed to translate audio. Status: " + response.getStatusCode() + ", Response: " + response.getBody());
            }
        } catch (IOException e) {
            throw new AudioTranslationException("Error processing audio file", e);
        } catch (RestClientException e) {
            throw new AudioTranslationException("Error calling local Whisper service", e);
        }
    }

    // Extrahera e-postadress från transkriberingen
    public static String extractEmailAddress(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String cleanEmailAddress(String text) {
        if (text == null) return "";

        // Steg 1: Sänk till gemener
        String cleaned = text.toLowerCase();

        // Steg 2: Ersätt typiska varianter av "at"
        cleaned = cleaned
                .replaceAll("\\b(snabel-?a|at)\\b", "@");

        // Steg 3: Ersätt typiska varianter av "dot" eller "punkt"
        cleaned = cleaned
                .replaceAll("\\b(dot|punkt|dotcom|dot net|dot org)\\b", ".");

        // Steg 4: Ersätt typiska varianter av "underscore" till "_"
        cleaned = cleaned
                .replaceAll("\\b(underscore|understreck)\\b", "_");

        // Steg 5: Rensa upp onödiga mellanslag runt punkter och snabel-a
        cleaned = cleaned
                .replaceAll("\\s*@\\s*", "@")
                .replaceAll("\\s*\\.\\s*", ".")
                .replaceAll("\\s*_\\s*", "_");

        // Steg 6: Återställ originaltext med e-posträttning på plats
        return cleaned.trim();
    }
}
