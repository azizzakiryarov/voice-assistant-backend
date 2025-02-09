package com.voiceassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.MeetingRepository;
import com.voiceassistant.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CommandProcessorService {
    private final OpenAIService openAIService;
    private final TodoRepository todoRepository;
    private final MeetingRepository meetingRepository;
    private final ObjectMapper objectMapper;

    public void processCommand(String text) throws Exception {
        String analysis = openAIService.analyzeCommand(text);
        var result = objectMapper.readTree(analysis);

        if (result.get("type").asText().equals("TODO")) {
            TodoItem todo = new TodoItem();
            todo.setDescription(result.get("description").asText());
            todo.setDueDate(LocalDateTime.parse(result.get("dueDate").asText()));
            todoRepository.save(todo);
        } else if (result.get("type").asText().equals("MEETING")) {
            Meeting meeting = new Meeting();
            meeting.setTitle(result.get("title").asText());
            meeting.setDateTime(LocalDateTime.parse(result.get("dateTime").asText()));
            meeting.setDuration(result.get("duration").asInt());
            meetingRepository.save(meeting);
        }
    }
}