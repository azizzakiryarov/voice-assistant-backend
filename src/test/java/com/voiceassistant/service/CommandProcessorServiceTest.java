package com.voiceassistant.service;

import com.voiceassistant.integration.google.service.GoogleCalendarService;
import com.voiceassistant.dto.MeetingRequestDTO;
import com.voiceassistant.dto.VoiceCommandApprovalRequestDTO;
import com.voiceassistant.dto.VoiceCommandApprovalResponseDTO;
import com.voiceassistant.dto.VoiceCommandType;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.Participants;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.MeetingRepository;
import com.voiceassistant.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandProcessorServiceTest {

    private OpenAIService openAIService;
    private GoogleCalendarService googleCalendarService;
    private TodoRepository todoRepository;
    private MeetingRepository meetingRepository;
    private CommandProcessorService commandProcessorService;

    @BeforeEach
    void setUp() {
        openAIService = mock(OpenAIService.class);
        googleCalendarService = mock(GoogleCalendarService.class);
        todoRepository = mock(TodoRepository.class);
        meetingRepository = mock(MeetingRepository.class);
        commandProcessorService = new CommandProcessorService(
                openAIService,
                googleCalendarService,
                todoRepository,
                meetingRepository);
    }

    @Test
    void processCommandReturnsSavedTodoItemAsJsonBody() {
        String command = "Lägg till att köpa mjölk imorgon";
        LocalDate dueDate = LocalDate.now().plusDays(1);

        TodoItem analyzedTodoItem = new TodoItem();
        analyzedTodoItem.setDescription("köpa mjölk");

        TodoItem savedTodoItem = new TodoItem();
        savedTodoItem.setId(42L);
        savedTodoItem.setDescription("köpa mjölk");
        savedTodoItem.setDueDate(dueDate);

        when(openAIService.analyzeCommand(command)).thenReturn(analyzedTodoItem);
        when(todoRepository.save(analyzedTodoItem)).thenReturn(savedTodoItem);

        ResponseEntity<Object> response = commandProcessorService.processCommand(command, dueDate, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(savedTodoItem);
        verify(todoRepository).save(analyzedTodoItem);
        assertThat(analyzedTodoItem.getDueDate()).isEqualTo(dueDate);
    }

    @Test
    void processCommandSetsTodayAsDueDateWhenTodoHasNoDate() {
        String command = "Lägg till att köpa mjölk";

        TodoItem analyzedTodoItem = new TodoItem();
        analyzedTodoItem.setDescription("köpa mjölk");

        TodoItem savedTodoItem = new TodoItem();
        savedTodoItem.setId(42L);
        savedTodoItem.setDescription("köpa mjölk");
        savedTodoItem.setDueDate(LocalDate.now());

        when(openAIService.analyzeCommand(command)).thenReturn(analyzedTodoItem);
        when(todoRepository.save(analyzedTodoItem)).thenReturn(savedTodoItem);

        ResponseEntity<Object> response = commandProcessorService.processCommand(command, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(savedTodoItem);
        assertThat(analyzedTodoItem.getDueDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void processCommandReturnsJsonErrorWhenAiCannotClassifyCommand() {
        String command = "Planera något";
        when(openAIService.analyzeCommand(command)).thenReturn(null);

        ResponseEntity<Object> response = commandProcessorService.processCommand(command, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("message", "Unknown command type"));
        verify(todoRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(meetingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void approveMeetingSavesMeetingEvenWhenGoogleCalendarSyncFails() throws IOException {
        Participants participant = new Participants();
        participant.setName("Peter Andersson");

        MeetingRequestDTO meetingRequest = new MeetingRequestDTO();
        meetingRequest.setTitle("Möte med Peter Andersson");
        meetingRequest.setStartTimestamp(LocalDateTime.of(2026, 6, 13, 10, 0));
        meetingRequest.setEndTimestamp(LocalDateTime.of(2026, 6, 13, 11, 0));
        meetingRequest.setParticipants(List.of(participant));

        VoiceCommandApprovalRequestDTO request = new VoiceCommandApprovalRequestDTO();
        request.setType(VoiceCommandType.MEETING);
        request.setMeeting(meetingRequest);

        Meeting savedMeeting = new Meeting();
        savedMeeting.setId(99L);
        savedMeeting.setTitle(meetingRequest.getTitle());
        savedMeeting.setStartTimestamp(meetingRequest.getStartTimestamp());
        savedMeeting.setEndTimestamp(meetingRequest.getEndTimestamp());
        savedMeeting.setParticipants(meetingRequest.getParticipants());

        when(meetingRepository.save(any(Meeting.class))).thenReturn(savedMeeting);
        doThrow(new IOException("Google unavailable")).when(googleCalendarService).createEvent(any());

        ResponseEntity<Object> response = commandProcessorService.approveCommand(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(VoiceCommandApprovalResponseDTO.class);
        VoiceCommandApprovalResponseDTO body = (VoiceCommandApprovalResponseDTO) response.getBody();
        assertThat(body.getType()).isEqualTo(VoiceCommandType.MEETING);
        assertThat(body.isGoogleSynced()).isFalse();
        assertThat(body.getSaved()).isSameAs(savedMeeting);
        verify(meetingRepository).save(any(Meeting.class));
    }
}
