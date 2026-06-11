package com.voiceassistant.service;

import com.voiceassistant.integration.google.service.GoogleCalendarService;
import com.voiceassistant.mapper.Mapper;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.Participants;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.MeetingRepository;
import com.voiceassistant.repository.TodoRepository;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static com.voiceassistant.service.TranscriptionService.extractEmailAddress;

@Service
public class CommandProcessorService {

    private final OpenAIService openAIService;
    private final GoogleCalendarService googleCalendarService;
    private final TodoRepository todoRepository;
    private final MeetingRepository meetingRepository;

    public CommandProcessorService(
            OpenAIService openAIService,
            GoogleCalendarService googleCalendarService,
            TodoRepository todoRepository,
            MeetingRepository meetingRepository) {
        this.openAIService = openAIService;
        this.googleCalendarService = googleCalendarService;
        this.todoRepository = todoRepository;
        this.meetingRepository = meetingRepository;
    }

    public ResponseEntity<Object> processCommand(String text, LocalDate dueDate, String email) {

        Object analysis = openAIService.analyzeCommand(text);

        if (analysis instanceof TodoItem todoItem) {
            if (dueDate != null) {
                todoItem.setDueDate(dueDate);
            } else if (todoItem.getDueDate() == null) {
                todoItem.setDueDate(LocalDate.now());
            }
            return processTodoItem(todoItem);
        } else if (analysis instanceof Meeting meeting) {
            String extractedEmail = extractEmailAddress(text);
            if (email == null || email.isEmpty()) {
                email = extractedEmail;
            }
            return processMeeting(meeting, email);
        }

        return ResponseEntity.badRequest().body(Map.of("message", "Unknown command type"));
    }

    private ResponseEntity<Object> processTodoItem(TodoItem todoItem) {
        if (todoItem.getDescription() == null || todoItem.getDescription().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todo details are incomplete"));
        }
        TodoItem savedTodoItem = todoRepository.save(todoItem);
        return ResponseEntity.ok(savedTodoItem);
    }

    private ResponseEntity<Object> processMeeting(Meeting meeting, String email) {

        if (email != null && !email.isBlank() && meeting.getParticipants() != null) {
            Optional<Participants> participants = meeting.getParticipants().stream().findFirst();
            participants.ifPresent(value -> value.setEmail(email));
        }

        if (isMeetingInvalid(meeting)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Meeting details are incomplete"));
        }
        try {
            googleCalendarService.createEvent(Mapper.mapMeetingToEvent(meeting, email));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Failed to save meeting to Google Calendar: " + e.getMessage()));
        }
        Meeting savedMeeting = meetingRepository.save(meeting);

        return ResponseEntity.ok(savedMeeting);
    }

    public ResponseEntity<Object> processConfirmedEmail(String transcription, String email) {
        return processCommand(transcription, null, email);
    }

    private boolean isMeetingInvalid(Meeting meeting) {
        return meeting.getTitle() == null || meeting.getTitle().isEmpty()
                || meeting.getStartTimestamp() == null
                || meeting.getEndTimestamp() == null
                || meeting.getParticipants() == null || meeting.getParticipants().isEmpty();
    }
}
