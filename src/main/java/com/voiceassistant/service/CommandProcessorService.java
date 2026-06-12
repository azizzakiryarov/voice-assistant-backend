package com.voiceassistant.service;

import com.voiceassistant.dto.TodoItemRequestDTO;
import com.voiceassistant.dto.VoiceCommandApprovalRequestDTO;
import com.voiceassistant.dto.VoiceCommandApprovalResponseDTO;
import com.voiceassistant.dto.VoiceCommandPreviewDTO;
import com.voiceassistant.dto.VoiceCommandType;
import com.voiceassistant.integration.google.service.GoogleCalendarService;
import com.voiceassistant.mapper.Mapper;
import com.voiceassistant.model.AppUser;
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
    private final AppUserService appUserService;

    public CommandProcessorService(
            OpenAIService openAIService,
            GoogleCalendarService googleCalendarService,
            TodoRepository todoRepository,
            MeetingRepository meetingRepository,
            AppUserService appUserService) {
        this.openAIService = openAIService;
        this.googleCalendarService = googleCalendarService;
        this.todoRepository = todoRepository;
        this.meetingRepository = meetingRepository;
        this.appUserService = appUserService;
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

    public VoiceCommandPreviewDTO previewCommand(String transcription) {
        VoiceCommandPreviewDTO preview = openAIService.analyzeVoiceCommand(transcription);
        preview.setTranscription(transcription);
        String extractedEmail = extractEmailAddress(transcription);
        preview.setExtractedEmail(extractedEmail);
        if (extractedEmail != null && preview.getMeeting() != null && preview.getMeeting().getParticipants() != null) {
            preview.getMeeting().getParticipants().stream()
                    .findFirst()
                    .ifPresent(participant -> participant.setEmail(extractedEmail));
        }
        return preview;
    }

    public ResponseEntity<Object> approveCommand(VoiceCommandApprovalRequestDTO request) {
        if (request.getType() == null || request.getType() == VoiceCommandType.UNKNOWN) {
            return ResponseEntity.badRequest().body(Map.of("message", "Command type is unknown"));
        }

        if (request.getType() == VoiceCommandType.TODO) {
            return approveTodo(request.getTodo());
        }

        return approveMeeting(request.getMeeting());
    }

    private ResponseEntity<Object> approveTodo(TodoItemRequestDTO request) {
        if (request == null || request.getDescription() == null || request.getDescription().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todo details are incomplete"));
        }

        AppUser owner = appUserService.getCurrentUser();
        TodoItem todoItem = new TodoItem();
        todoItem.setDescription(request.getDescription());
        todoItem.setDueDate(request.getDueDate() != null ? request.getDueDate() : LocalDate.now());
        todoItem.setCompleted(request.isCompleted());
        todoItem.setOwner(owner);
        todoItem.setSyncStatus("LOCAL");

        TodoItem saved = todoRepository.save(todoItem);
        VoiceCommandApprovalResponseDTO response = new VoiceCommandApprovalResponseDTO();
        response.setType(VoiceCommandType.TODO);
        response.setSaved(saved);
        response.setGoogleSynced(false);
        response.setGoogleService("NONE");
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Object> approveMeeting(com.voiceassistant.dto.MeetingRequestDTO request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Meeting details are incomplete"));
        }

        AppUser owner = appUserService.getCurrentUser();
        Meeting meeting = new Meeting();
        meeting.setTitle(request.getTitle());
        meeting.setStartTimestamp(request.getStartTimestamp());
        meeting.setEndTimestamp(request.getEndTimestamp());
        meeting.setParticipants(request.getParticipants());
        meeting.setOwner(owner);

        if (isMeetingInvalid(meeting)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Meeting details are incomplete"));
        }

        Meeting saved = meetingRepository.save(meeting);
        String email = meeting.getParticipants().stream()
                .map(Participants::getEmail)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        boolean googleSynced = false;
        try {
            googleCalendarService.createEvent(Mapper.mapMeetingToEvent(meeting, email));
            googleSynced = true;
        } catch (IOException e) {
            // The approved command is already persisted locally; surface sync state to the client.
        }

        VoiceCommandApprovalResponseDTO response = new VoiceCommandApprovalResponseDTO();
        response.setType(VoiceCommandType.MEETING);
        response.setSaved(saved);
        response.setGoogleSynced(googleSynced);
        response.setGoogleService("GOOGLE_CALENDAR");
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Object> processTodoItem(TodoItem todoItem) {
        if (todoItem.getDescription() == null || todoItem.getDescription().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todo details are incomplete"));
        }
        todoItem.setOwner(appUserService.getCurrentUser());
        todoItem.setSyncStatus("LOCAL");
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
        meeting.setOwner(appUserService.getCurrentUser());
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
