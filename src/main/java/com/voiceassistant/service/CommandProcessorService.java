package com.voiceassistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceassistant.exception.AudioTranslationException;
import com.voiceassistant.integration.google.service.GoogleCalendarService;
import com.voiceassistant.mapper.EventMapper;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.MeetingRepository;
import com.voiceassistant.repository.TodoRepository;
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

@Service
public class CommandProcessorService {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final OpenAIService openAIService;
    private final GoogleCalendarService googleCalendarService;
    private final TodoRepository todoRepository;
    private final MeetingRepository meetingRepository;

    public CommandProcessorService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            OpenAIService openAIService,
            GoogleCalendarService googleCalendarService,
            TodoRepository todoRepository,
            MeetingRepository meetingRepository,
            @Value("${spring.ai.openai.api-key}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.openAIService = openAIService;
        this.googleCalendarService = googleCalendarService;
        this.todoRepository = todoRepository;
        this.meetingRepository = meetingRepository;
        this.apiKey = apiKey;
    }

    public ResponseEntity<String> processCommand(String text) {

        Object analysis = openAIService.analyzeCommand(text);

        if (analysis instanceof TodoItem todoItem) {
            return processTodoItem(todoItem);
        } else if (analysis instanceof Meeting meeting) {
            return processMeeting(meeting);
        }

        return ResponseEntity.status(400).body("Unknown command type");
    }

    private ResponseEntity<String> processTodoItem(TodoItem todoItem) {
        if (todoItem.getDescription() == null || todoItem.getDescription().isEmpty()) {
            return ResponseEntity.status(400).body("Todo details are incomplete");
        }
        todoRepository.save(todoItem);
        return ResponseEntity.ok("Todo saved successfully");
    }

    private ResponseEntity<String> processMeeting(Meeting meeting) {
        if (isMeetingInvalid(meeting)) {
            return ResponseEntity.status(400).body("Meeting details are incomplete");
        }

        meetingRepository.save(meeting);
        try {
            googleCalendarService.createEvent(EventMapper.mapMeetingToEvent(meeting));
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Failed to save meeting to Google Calendar: " + e.getMessage());
        }

        return ResponseEntity.ok("Meeting saved successfully and added to Google Calendar");
    }

    private boolean isMeetingInvalid(Meeting meeting) {
        return meeting.getTitle() == null || meeting.getTitle().isEmpty()
                || meeting.getStartTimestamp() == null
                || meeting.getEndTimestamp() == null
                || meeting.getParticipants() == null || meeting.getParticipants().isEmpty();
    }

    public ResponseEntity<String> translateAndSave(MultipartFile audioFile) {
        String translatedText = translateAudioToEnglish(audioFile);
        return processCommand(translatedText);
    }

    public String translateAudioToEnglish(MultipartFile audioFile) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return Optional.ofNullable(audioFile.getOriginalFilename()).orElse("audio.wav");
                }
            });
            body.add("model", "whisper-1");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            String apiUrl = "https://api.openai.com/v1/audio/translations";

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return extractTranslatedText(response.getBody());
            } else {
                throw new AudioTranslationException("Failed to translate audio. Status: " + response.getStatusCode() + ", Response: " + response.getBody());
            }
        } catch (IOException e) {
            throw new AudioTranslationException("Error processing audio file", e);
        } catch (RestClientException e) {
            throw new AudioTranslationException("Error calling translation API", e);
        }
    }

    private String extractTranslatedText(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return jsonNode.path("text").asText();
        } catch (JsonProcessingException e) {
            throw new AudioTranslationException("Failed to parse translation response", e);
        }
    }
}