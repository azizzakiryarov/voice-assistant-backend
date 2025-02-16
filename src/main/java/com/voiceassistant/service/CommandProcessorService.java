package com.voiceassistant.service;

import com.voiceassistant.exception.AudioTranslationException;
import com.voiceassistant.integration.google.service.GoogleCalendarService;
import com.voiceassistant.mapper.EventMapper;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.MeetingRepository;
import com.voiceassistant.repository.TodoRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@Service
public class CommandProcessorService {

    private final OpenAIService openAIService;
    private final GoogleCalendarService googleCalendarService;
    private final TodoRepository todoRepository;
    private final MeetingRepository meetingRepository;
    private final String apiKey;

    public CommandProcessorService(
            OpenAIService openAIService,
            GoogleCalendarService googleCalendarService,
            TodoRepository todoRepository,
            MeetingRepository meetingRepository,
            @Value("${spring.ai.openai.api-key}") String apiKey
    ) {
        this.openAIService = openAIService;
        this.googleCalendarService = googleCalendarService;
        this.todoRepository = todoRepository;
        this.meetingRepository = meetingRepository;
        this.apiKey = apiKey;
    }

    public void processCommand(String text) throws IOException {

        Object analysis = openAIService.analyzeCommand(text);

        if (analysis instanceof TodoItem) {
            todoRepository.save((TodoItem) analysis);
        } else if (analysis instanceof Meeting) {
            meetingRepository.save((Meeting) analysis);
            googleCalendarService.createEvent(EventMapper.mapMeetingToEvent((Meeting) analysis));
        }
    }

    public void translateAndSave(MultipartFile audioFile) throws IOException {
        String translatedText = translateAudioToEnglish(audioFile);
        processCommand(translatedText);
    }

    private String translateAudioToEnglish(MultipartFile audioFile) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(apiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(audioFile.getBytes()) {
            @Override
            public String getFilename() {
                return audioFile.getOriginalFilename();
            }
        });
        body.add("model", "whisper-1");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        String apiUrl = "https://api.openai.com/v1/audio/translations";
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JSONObject jsonResponse = new JSONObject(response.getBody());
            return jsonResponse.getString("text");
        } else {
            throw new AudioTranslationException("Failed to translate audio. Response: " + response.getBody());
        }
    }

    public List<TodoItem> getTodos() {
        return todoRepository.findAll();
    }
}