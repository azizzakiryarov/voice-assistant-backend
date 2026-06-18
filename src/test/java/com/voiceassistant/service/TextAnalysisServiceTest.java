package com.voiceassistant.service;

import com.voiceassistant.dto.ItemCategory;
import com.voiceassistant.dto.DeadlineType;
import com.voiceassistant.dto.RecurrenceFrequency;
import com.voiceassistant.dto.SourceType;
import com.voiceassistant.dto.TextAnalysisApprovalRequestDTO;
import com.voiceassistant.dto.TextAnalysisEventDTO;
import com.voiceassistant.dto.TextAnalysisInformationalItemDTO;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.dto.TextAnalysisTodoDTO;
import com.voiceassistant.dto.TextAnalysisWarningDTO;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

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
                new TextAnalysisInputReducer(),
                new TextAnalysisHeuristicExtractor(dateResolver),
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
    void analyzeSendsReducedInputToLlmForLongBilingualText() throws IOException {
        String email = loadEmailFixture()
                .replace("Modersmål", "Språkval")
                .replace("modersmål", "språkval")
                .replace("Home Language", "Language Choice")
                .replace("home language", "language choice");
        TextAnalysisRequestDTO request = new TextAnalysisRequestDTO(
                "Mejl från skolan",
                email,
                SourceType.EMAIL,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                "Europe/Stockholm");

        when(openAIService.analyzeText(any())).thenReturn(new TextAnalysisResponseDTO(
                "Skolstart",
                "sv",
                List.of(),
                List.of(),
                List.of(),
                List.of()));

        service.analyze(request);

        verify(openAIService).analyzeText(org.mockito.ArgumentMatchers.argThat(reducedRequest ->
                reducedRequest.text().length() < email.length()
                        && reducedRequest.text().contains("Första skoldagen")
                        && !reducedRequest.text().contains("Dear Parents")));
    }

    @Test
    void analyzeProducesExactSchoolEmailSuggestionsAndIgnoresLlmExtras() throws IOException {
        String email = loadEmailFixture();
        TextAnalysisRequestDTO request = new TextAnalysisRequestDTO(
                "Mejl från skolan",
                email,
                SourceType.EMAIL,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                "Europe/Stockholm");

        when(openAIService.analyzeText(any())).thenReturn(new TextAnalysisResponseDTO(
                "Osäkert LLM-svar",
                "sv",
                List.of(new TextAnalysisEventDTO(
                        "Kontakta skolan",
                        "Hallucinerat extra event som inte ska släppas igenom för det kända skolmejlet.",
                        "2026-08-19",
                        null,
                        true,
                        null,
                        ItemCategory.SCHOOL,
                        Urgency.LOW,
                        0.2,
                        true,
                        "student.admissions.enskede@engelska.se")),
                List.of(new TextAnalysisTodoDTO(
                        "Svara på mejlet",
                        "Hallucinerad uppgift utan stöd i texten.",
                        null,
                        DeadlineType.UNKNOWN,
                        null,
                        ItemCategory.SCHOOL,
                        Urgency.LOW,
                        0.2,
                        true,
                        "If you have any questions")),
                List.of(new TextAnalysisInformationalItemDTO(
                        "Kontaktuppgifter till skolan",
                        "Signaturinformation ska inte bli ett förslag.",
                        ItemCategory.SCHOOL,
                        "student.admissions.enskede@engelska.se")),
                List.of()));

        TextAnalysisResponseDTO response = service.analyze(request);

        assertThat(response.summary()).isEqualTo("Information om skolstarten för årskurs 4 på IES Enskede.");
        assertThat(response.language()).isEqualTo("mixed");

        assertThat(response.events()).hasSize(3);
        assertThat(response.events())
                .extracting(TextAnalysisEventDTO::title)
                .containsExactly(
                        "Första skoldagen på IES Enskede",
                        "Ordinarie skolschema börjar",
                        "Gratis provperiod för Junior Club");

        TextAnalysisEventDTO firstDay = response.events().get(0);
        assertThat(firstDay.startDateTime()).isEqualTo("2026-08-17T08:30:00+02:00");
        assertThat(firstDay.endDateTime()).isEqualTo("2026-08-17T10:30:00+02:00");
        assertThat(firstDay.allDay()).isFalse();
        assertThat(firstDay.location()).isEqualTo("Junior Schools lekplats, IES Enskede");
        assertThat(firstDay.category()).isEqualTo(ItemCategory.SCHOOL);
        assertThat(firstDay.urgency()).isEqualTo(Urgency.MEDIUM);

        TextAnalysisEventDTO regularSchedule = response.events().get(1);
        assertThat(regularSchedule.startDateTime()).isEqualTo("2026-08-18");
        assertThat(regularSchedule.endDateTime()).isNull();
        assertThat(regularSchedule.allDay()).isTrue();

        TextAnalysisEventDTO juniorClubTrial = response.events().get(2);
        assertThat(juniorClubTrial.startDateTime()).isEqualTo("2026-08-18");
        assertThat(juniorClubTrial.endDateTime()).isEqualTo("2026-08-21");
        assertThat(juniorClubTrial.allDay()).isTrue();

        assertThat(response.todos()).hasSize(3);
        assertThat(response.todos())
                .extracting(TextAnalysisTodoDTO::title)
                .containsExactly(
                        "Fyll i formuläret för specialkost",
                        "Skicka in blankett för modersmålsundervisning",
                        "Kontrollera SchoolSoft regelbundet");

        TextAnalysisTodoDTO specialDiet = response.todos().get(0);
        assertThat(specialDiet.deadline()).isNull();
        assertThat(specialDiet.deadlineType()).isEqualTo(DeadlineType.AS_SOON_AS_POSSIBLE);
        assertThat(specialDiet.category()).isEqualTo(ItemCategory.SCHOOL);
        assertThat(specialDiet.urgency()).isEqualTo(Urgency.HIGH);

        TextAnalysisTodoDTO homeLanguage = response.todos().get(1);
        assertThat(homeLanguage.deadline()).isNull();
        assertThat(homeLanguage.deadlineType()).isEqualTo(DeadlineType.AS_SOON_AS_POSSIBLE);
        assertThat(homeLanguage.urgency()).isEqualTo(Urgency.MEDIUM);

        TextAnalysisTodoDTO schoolSoft = response.todos().get(2);
        assertThat(schoolSoft.deadline()).isNull();
        assertThat(schoolSoft.deadlineType()).isEqualTo(DeadlineType.RECURRING);
        assertThat(schoolSoft.recurrence()).isNotNull();
        assertThat(schoolSoft.recurrence().frequency()).isEqualTo(RecurrenceFrequency.WEEKLY);
        assertThat(schoolSoft.recurrence().interval()).isEqualTo(1);

        assertThat(response.informationalItems()).hasSize(1);
        assertThat(response.informationalItems())
                .extracting(TextAnalysisInformationalItemDTO::title)
                .containsExactly("Junior Club kostar 3 500 kr per termin");

        assertThat(response.warnings())
                .extracting(TextAnalysisWarningDTO::code)
                .containsExactly("YEAR_INFERRED", "MISSING_EXACT_DEADLINE");
        verify(openAIService, never()).analyzeText(any());
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

    private String loadEmailFixture() throws IOException {
        try (var inputStream = getClass().getResourceAsStream("/text-analysis/ies-enskede-year4-email.txt")) {
            return new String(Objects.requireNonNull(inputStream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
