package com.voiceassistant.service;

import com.voiceassistant.dto.ItemCategory;
import com.voiceassistant.dto.DeadlineType;
import com.voiceassistant.dto.SourceType;
import com.voiceassistant.dto.TextAnalysisApprovalRequestDTO;
import com.voiceassistant.dto.TextAnalysisEventDTO;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.dto.TextAnalysisTodoDTO;
import com.voiceassistant.dto.Urgency;
import com.voiceassistant.integration.google.service.GoogleCalendarService;
import com.voiceassistant.integration.google.service.GoogleTasksService;
import com.voiceassistant.model.AppUser;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.MeetingRepository;
import com.voiceassistant.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TextAnalysisServiceTest {

    private OpenAIService openAIService;
    private TodoRepository todoRepository;
    private MeetingRepository meetingRepository;
    private AppUserService appUserService;
    private GoogleCalendarService googleCalendarService;
    private GoogleTasksService googleTasksService;
    private TextAnalysisService service;

    @BeforeEach
    void setUp() {
        openAIService = mock(OpenAIService.class);
        todoRepository = mock(TodoRepository.class);
        meetingRepository = mock(MeetingRepository.class);
        appUserService = mock(AppUserService.class);
        googleCalendarService = mock(GoogleCalendarService.class);
        googleTasksService = mock(GoogleTasksService.class);

        TextAnalysisDateResolver dateResolver = new TextAnalysisDateResolver();
        service = new TextAnalysisService(
                openAIService,
                new TextAnalysisPostProcessor(dateResolver, new TextAnalysisUrgencyResolver()),
                dateResolver,
                appUserService,
                todoRepository,
                meetingRepository,
                googleCalendarService,
                googleTasksService);
    }

    @Test
    void analyzeDoesNotPersistAnythingBeforeApproval() {
        TextAnalysisRequestDTO request = new TextAnalysisRequestDTO(
                "Mejl från skolan",
                "Första skoldagen är den 17 augusti kl. 08:30–10:30.",
                SourceType.EMAIL,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                "Europe/Stockholm");

        when(openAIService.analyzeText(any())).thenReturn(new TextAnalysisResponseDTO(
                "Skolstart",
                "sv",
                List.of(new TextAnalysisEventDTO(
                        "Första skoldagen",
                        "Samling",
                        "2026-08-17T08:30:00+02:00",
                        "2026-08-17T10:30:00+02:00",
                        false,
                        "Junior Schools lekplats",
                        ItemCategory.SCHOOL,
                        Urgency.MEDIUM,
                        0.98,
                        true,
                        "Första skoldagen är den 17 augusti kl. 08:30–10:30.")),
                List.of(),
                List.of(),
                List.of()));

        TextAnalysisResponseDTO response = service.analyze(request);

        assertThat(response.events()).hasSize(1);
        verify(todoRepository, never()).save(any());
        verify(meetingRepository, never()).save(any());
    }

    @Test
    void approveCreatesEventsForCurrentUser() {
        AppUser user = new AppUser();
        user.setId(5L);
        when(appUserService.getCurrentUser()).thenReturn(user);
        when(googleCalendarService.hasCurrentUserCalendarToken()).thenReturn(false);
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> {
            Meeting meeting = invocation.getArgument(0);
            meeting.setId(42L);
            return meeting;
        });

        TextAnalysisApprovalRequestDTO request = new TextAnalysisApprovalRequestDTO(
                List.of(new TextAnalysisEventDTO(
                        "Första skoldagen",
                        "Samling",
                        "2026-08-17T08:30:00+02:00",
                        "2026-08-17T10:30:00+02:00",
                        false,
                        "Junior Schools lekplats",
                        ItemCategory.SCHOOL,
                        Urgency.MEDIUM,
                        0.98,
                        true,
                        "Första skoldagen är den 17 augusti kl. 08:30–10:30.")),
                List.of());

        var response = service.approve(request);

        assertThat(response.createdEvents()).hasSize(1);
        verify(meetingRepository).save(org.mockito.ArgumentMatchers.argThat(meeting -> meeting.getOwner() == user));
    }

    @Test
    void approveCreatesTodosForCurrentUser() {
        AppUser user = new AppUser();
        user.setId(7L);
        when(appUserService.getCurrentUser()).thenReturn(user);
        when(googleTasksService.createTaskIfConnected(any(TodoItem.class))).thenReturn(false);
        when(todoRepository.save(any(TodoItem.class))).thenAnswer(invocation -> {
            TodoItem todoItem = invocation.getArgument(0);
            todoItem.setId(77L);
            return todoItem;
        });

        TextAnalysisApprovalRequestDTO request = new TextAnalysisApprovalRequestDTO(
                List.of(),
                List.of(new TextAnalysisTodoDTO(
                        "Fyll i formuläret för specialkost",
                        "Barnet behöver specialkost eller har allergier.",
                        null,
                        DeadlineType.AS_SOON_AS_POSSIBLE,
                        null,
                        ItemCategory.SCHOOL,
                        Urgency.HIGH,
                        0.97,
                        true,
                        "Fyll i formuläret för specialkost så snart som möjligt.")));

        var response = service.approve(request);

        assertThat(response.createdTodos()).hasSize(1);
        verify(todoRepository).save(org.mockito.ArgumentMatchers.argThat(todo -> todo.getOwner() == user));
    }
}
