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
import java.util.List;

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

    public ResponseEntity<String> processCommand(String text, String email) {

        Object analysis = openAIService.analyzeCommand(text);

        if (analysis instanceof TodoItem todoItem) {
            return processTodoItem(todoItem);
        } else if (analysis instanceof Meeting meeting) {
            return processMeeting(meeting, email);
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

    private ResponseEntity<String> processMeeting(Meeting meeting, String email) {

        if(!email.isEmpty()) {
            List<Participants> existedParticipant = meeting.getParticipants();
            for (Participants participant : existedParticipant) {
                if (participant.getEmail().equals(email)) {
                    return ResponseEntity.status(400).body("Participant with this email already exists");
                }
                participant.setEmail(email);
            }
        }

        if (isMeetingInvalid(meeting)) {
            return ResponseEntity.status(400).body("Meeting details are incomplete");
        }
        try {
            googleCalendarService.createEvent(Mapper.mapMeetingToEvent(meeting, email));
        } catch (IOException e) {
            return ResponseEntity.status(400).body("Failed to save meeting to Google Calendar: " + e.getMessage());
        }
        meetingRepository.save(meeting);

        return ResponseEntity.ok("Meeting saved successfully and added to Google Calendar");
    }

    public void processConfirmedEmail(String email, String transcription) {
        processCommand(transcription, email);
    }

    private boolean isMeetingInvalid(Meeting meeting) {
        return meeting.getTitle() == null || meeting.getTitle().isEmpty()
                || meeting.getStartTimestamp() == null
                || meeting.getEndTimestamp() == null
                || meeting.getParticipants() == null || meeting.getParticipants().isEmpty();
    }
}