package com.voiceassistant.service;

import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.MeetingRepository;
import com.voiceassistant.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommandProcessorService {

    private final OpenAIService openAIService;
    private final TodoRepository todoRepository;
    private final MeetingRepository meetingRepository;

    public void processCommand(String text) {
        Object analysis = openAIService.analyzeCommand(text);

        if (analysis instanceof TodoItem) {
            todoRepository.save((TodoItem) analysis);
        } else if (analysis instanceof Meeting) {
            meetingRepository.save((Meeting) analysis);
        }
    }
}