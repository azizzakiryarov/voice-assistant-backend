package com.voiceassistant.service;

import com.voiceassistant.exception.AudioTranslationException;
import com.voiceassistant.dto.TranscriptionResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.voiceassistant.mapper.Mapper.extractTranslatedText;

@Service
public class TranscriptionService {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/audio/translations";
    private final String openaiApiKey;
    private final RestTemplate restTemplate;

    public TranscriptionService(RestTemplate restTemplate,
                                @Value("${spring.ai.openai.api-key}") String openaiApiKey) {
        this.restTemplate = restTemplate;
        this.openaiApiKey = openaiApiKey;
    }

    // Regular expression för att hitta e-postadresser
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    public TranscriptionResponseDTO transcribeAudio(MultipartFile audioFile) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(openaiApiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return Optional.ofNullable(audioFile.getOriginalFilename()).orElse("audio.wav");
                }
            });
            body.add("model", "whisper-1");
            body.add("response_format", "json");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_API_URL, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String translatedText = extractTranslatedText(response.getBody());
                // Extrahera e-postadress från transkriberingen
                TranscriptionResponseDTO transcriptionResponseDTO = new TranscriptionResponseDTO();
                String cleanedEmailAddress = cleanEmailAddress(translatedText);
                transcriptionResponseDTO.setTranscription(cleanedEmailAddress);
                Matcher matcher = EMAIL_PATTERN.matcher(cleanedEmailAddress);
                if (matcher.find()) {
                    String email = matcher.group();
                    transcriptionResponseDTO.setExtractedEmail(email);
                }

                return transcriptionResponseDTO;
            } else {
                throw new AudioTranslationException("Failed to translate audio. Status: " + response.getStatusCode() + ", Response: " + response.getBody());
            }
        } catch (IOException e) {
            throw new AudioTranslationException("Error processing audio file", e);
        } catch (RestClientException e) {
            throw new AudioTranslationException("Error calling translation API", e);
        }
    }

    private String cleanEmailAddress(String text) {
        return text
                .replaceAll("(?i)\\s+dot\\s+", ".")   // "dot" -> "."
                .replaceAll("(?i)\\s+at\\s+", "@")     // "at" -> "@"
                .replaceAll("(?i)\\s+underscore\\s+", "_") // optional: "underscore" -> "_"
                .replaceAll("\\s+", " ")              // rensa överflödiga mellanslag
                .trim();
    }
}