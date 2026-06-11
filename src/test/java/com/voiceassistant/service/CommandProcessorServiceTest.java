package com.voiceassistant.service;

import com.voiceassistant.integration.google.service.GoogleCalendarService;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.MeetingRepository;
import com.voiceassistant.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
}
