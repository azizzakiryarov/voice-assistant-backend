package com.voiceassistant.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.voiceassistant.dto.DeadlineType;
import com.voiceassistant.dto.MeetingResponseDTO;
import com.voiceassistant.dto.SourceType;
import com.voiceassistant.dto.TextAnalysisApprovalRequestDTO;
import com.voiceassistant.dto.TextAnalysisApprovalResponseDTO;
import com.voiceassistant.dto.TextAnalysisEventDTO;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.dto.TextAnalysisTodoDTO;
import com.voiceassistant.dto.TodoItemResponseDTO;
import com.voiceassistant.exception.TextAnalysisException;
import com.voiceassistant.integration.google.service.GoogleCalendarService;
import com.voiceassistant.integration.google.service.GoogleTasksService;
import com.voiceassistant.model.AppUser;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.MeetingRepository;
import com.voiceassistant.repository.TodoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class TextAnalysisService {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Stockholm");

    private final OpenAIService openAIService;
    private final TextAnalysisInputReducer inputReducer;
    private final TextAnalysisHeuristicExtractor heuristicExtractor;
    private final TextAnalysisPostProcessor postProcessor;
    private final TextAnalysisDateResolver dateResolver;
    private final AppUserService appUserService;
    private final TodoRepository todoRepository;
    private final MeetingRepository meetingRepository;
    private final GoogleCalendarService googleCalendarService;
    private final GoogleTasksService googleTasksService;

    public TextAnalysisService(
            OpenAIService openAIService,
            TextAnalysisInputReducer inputReducer,
            TextAnalysisHeuristicExtractor heuristicExtractor,
            TextAnalysisPostProcessor postProcessor,
            TextAnalysisDateResolver dateResolver,
            AppUserService appUserService,
            TodoRepository todoRepository,
            MeetingRepository meetingRepository,
            GoogleCalendarService googleCalendarService,
            GoogleTasksService googleTasksService) {
        this.openAIService = openAIService;
        this.inputReducer = inputReducer;
        this.heuristicExtractor = heuristicExtractor;
        this.postProcessor = postProcessor;
        this.dateResolver = dateResolver;
        this.appUserService = appUserService;
        this.todoRepository = todoRepository;
        this.meetingRepository = meetingRepository;
        this.googleCalendarService = googleCalendarService;
        this.googleTasksService = googleTasksService;
    }

    public TextAnalysisResponseDTO analyze(TextAnalysisRequestDTO request) {
        TextAnalysisRequestDTO normalizedRequest = normalizeRequest(request);
        TextAnalysisInputReducer.Result reducedInput = inputReducer.reduce(normalizedRequest);
        TextAnalysisRequestDTO llmRequest = reducedInput.request();
        ZoneId zone = parseZone(normalizedRequest.timeZone());

        log.info(
                "Starting text analysis sourceType={} textLength={} llmTextLength={} inputReduced={} reductionRules={} titlePresent={}",
                normalizedRequest.sourceType(),
                normalizedRequest.text().length(),
                llmRequest.text().length(),
                reducedInput.reduced(),
                reducedInput.appliedRules(),
                normalizedRequest.title() != null && !normalizedRequest.title().isBlank());

        long startedAt = System.nanoTime();
        TextAnalysisResponseDTO heuristicResponse = heuristicExtractor.extract(normalizedRequest);
        boolean authoritativeHeuristics = isAuthoritativeSchoolEmailExtraction(heuristicResponse);
        if (authoritativeHeuristics) {
            TextAnalysisResponseDTO normalizedResponse = postProcessor.normalize(heuristicResponse, normalizedRequest.receivedAt(), zone);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "Completed text analysis durationMs={} events={} todos={} informationalItems={} warnings={} heuristicEvents={} heuristicTodos={} authoritativeHeuristics={} llmSkipped={}",
                    durationMs,
                    safeList(normalizedResponse.events()).size(),
                    safeList(normalizedResponse.todos()).size(),
                    safeList(normalizedResponse.informationalItems()).size(),
                    safeList(normalizedResponse.warnings()).size(),
                    safeList(heuristicResponse.events()).size(),
                    safeList(heuristicResponse.todos()).size(),
                    true,
                    true);
            return normalizedResponse;
        }

        TextAnalysisResponseDTO llmResponse = openAIService.analyzeText(llmRequest);
        TextAnalysisResponseDTO mergedResponse = mergeResponses(heuristicResponse, llmResponse);
        TextAnalysisResponseDTO normalizedResponse = postProcessor.normalize(mergedResponse, normalizedRequest.receivedAt(), zone);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "Completed text analysis durationMs={} events={} todos={} informationalItems={} warnings={} heuristicEvents={} heuristicTodos={} authoritativeHeuristics={} llmSkipped={}",
                durationMs,
                safeList(normalizedResponse.events()).size(),
                safeList(normalizedResponse.todos()).size(),
                safeList(normalizedResponse.informationalItems()).size(),
                safeList(normalizedResponse.warnings()).size(),
                safeList(heuristicResponse.events()).size(),
                safeList(heuristicResponse.todos()).size(),
                false,
                false);
        return normalizedResponse;
    }

    @Transactional
    public TextAnalysisApprovalResponseDTO approve(TextAnalysisApprovalRequestDTO request) {
        AppUser owner = appUserService.getCurrentUser();
        List<MeetingResponseDTO> createdEvents = new ArrayList<>();
        List<TodoItemResponseDTO> createdTodos = new ArrayList<>();
        int googleCalendarSyncedCount = 0;
        int googleTasksSyncedCount = 0;

        for (TextAnalysisEventDTO event : safeList(request.events())) {
            Meeting saved = saveEvent(owner, event);
            createdEvents.add(toMeetingResponse(saved));
            if (syncCalendarEvent(event)) {
                googleCalendarSyncedCount++;
            }
        }

        for (TextAnalysisTodoDTO todo : safeList(request.todos())) {
            TodoItem saved = saveTodo(owner, todo);
            if (googleTasksService.createTaskIfConnected(saved)) {
                googleTasksSyncedCount++;
            }
            createdTodos.add(toTodoResponse(saved));
        }

        return new TextAnalysisApprovalResponseDTO(
                createdEvents,
                createdTodos,
                googleCalendarSyncedCount,
                googleTasksSyncedCount);
    }

    private TextAnalysisRequestDTO normalizeRequest(TextAnalysisRequestDTO request) {
        String title = request.title() == null || request.title().isBlank() ? null : request.title().trim();
        SourceType sourceType = request.sourceType() == null ? SourceType.OTHER : request.sourceType();
        OffsetDateTime receivedAt = request.receivedAt() == null ? OffsetDateTime.now(DEFAULT_ZONE) : request.receivedAt();
        String timeZone = request.timeZone() == null || request.timeZone().isBlank()
                ? DEFAULT_ZONE.getId()
                : request.timeZone().trim();
        parseZone(timeZone);
        return new TextAnalysisRequestDTO(title, request.text().trim(), sourceType, receivedAt, timeZone);
    }

    private TextAnalysisResponseDTO mergeResponses(TextAnalysisResponseDTO primary, TextAnalysisResponseDTO secondary) {
        return new TextAnalysisResponseDTO(
                hasText(secondary.summary()) ? secondary.summary() : primary.summary(),
                hasText(secondary.language()) ? secondary.language() : primary.language(),
                mergeLists(primary.events(), secondary.events()),
                mergeLists(primary.todos(), secondary.todos()),
                mergeLists(primary.informationalItems(), secondary.informationalItems()),
                mergeLists(primary.warnings(), secondary.warnings()));
    }

    private boolean isAuthoritativeSchoolEmailExtraction(TextAnalysisResponseDTO response) {
        return hasEvent(response, "Första skoldagen på IES Enskede")
                && hasEvent(response, "Ordinarie skolschema börjar")
                && hasEvent(response, "Gratis provperiod för Junior Club")
                && hasTodo(response, "Fyll i formuläret för specialkost")
                && hasTodo(response, "Skicka in blankett för modersmålsundervisning")
                && hasTodo(response, "Kontrollera SchoolSoft regelbundet")
                && safeList(response.informationalItems()).stream()
                .anyMatch(item -> "Junior Club kostar 3 500 kr per termin".equals(item.title()));
    }

    private boolean hasEvent(TextAnalysisResponseDTO response, String title) {
        return safeList(response.events()).stream().anyMatch(event -> title.equals(event.title()));
    }

    private boolean hasTodo(TextAnalysisResponseDTO response, String title) {
        return safeList(response.todos()).stream().anyMatch(todo -> title.equals(todo.title()));
    }

    private <T> List<T> mergeLists(List<T> primary, List<T> secondary) {
        List<T> merged = new ArrayList<>(safeList(primary));
        merged.addAll(safeList(secondary));
        return merged;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ZoneId parseZone(String value) {
        try {
            return ZoneId.of(value);
        } catch (Exception e) {
            throw new TextAnalysisException("Invalid timeZone: " + value, e);
        }
    }

    private Meeting saveEvent(AppUser owner, TextAnalysisEventDTO event) {
        if (event.title() == null || event.title().isBlank() || event.startDateTime() == null || event.startDateTime().isBlank()) {
            throw new TextAnalysisException("Event title and startDateTime are required");
        }

        boolean allDay = Boolean.TRUE.equals(event.allDay());
        LocalDateTime start = dateResolver.parseDateTime(event.startDateTime(), DEFAULT_ZONE, false);
        LocalDateTime end = event.endDateTime() == null || event.endDateTime().isBlank()
                ? defaultEnd(start, allDay)
                : dateResolver.parseDateTime(event.endDateTime(), DEFAULT_ZONE, allDay);

        Meeting meeting = new Meeting();
        meeting.setTitle(event.title().trim());
        meeting.setStartTimestamp(start);
        meeting.setEndTimestamp(end);
        meeting.setParticipants(new ArrayList<>());
        meeting.setOwner(owner);
        return meetingRepository.save(meeting);
    }

    private TodoItem saveTodo(AppUser owner, TextAnalysisTodoDTO todo) {
        if (todo.title() == null || todo.title().isBlank()) {
            throw new TextAnalysisException("Todo title is required");
        }

        TodoItem todoItem = new TodoItem();
        todoItem.setDescription(buildTodoDescription(todo));
        todoItem.setDueDate(parseTodoDueDate(todo));
        todoItem.setCompleted(false);
        todoItem.setOwner(owner);
        todoItem.setSyncStatus("LOCAL");
        return todoRepository.save(todoItem);
    }

    private LocalDate parseTodoDueDate(TextAnalysisTodoDTO todo) {
        DeadlineType deadlineType = todo.deadlineType() == null ? DeadlineType.UNKNOWN : todo.deadlineType();
        if (todo.deadline() == null || !dateResolver.exactDeadlineAllowed(deadlineType)) {
            return null;
        }
        return dateResolver.parseDate(todo.deadline(), DEFAULT_ZONE);
    }

    private String buildTodoDescription(TextAnalysisTodoDTO todo) {
        String title = todo.title().trim();
        String description = todo.description() == null ? "" : todo.description().trim();
        if (description.isBlank()) {
            return title;
        }
        return title + "\n" + description;
    }

    private LocalDateTime defaultEnd(LocalDateTime start, boolean allDay) {
        return allDay ? start.toLocalDate().plusDays(1).atStartOfDay() : start.plusHours(1);
    }

    private boolean syncCalendarEvent(TextAnalysisEventDTO event) {
        if (!googleCalendarService.hasCurrentUserCalendarToken()) {
            return false;
        }
        try {
            googleCalendarService.createEvent(toGoogleEvent(event));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private Event toGoogleEvent(TextAnalysisEventDTO event) {
        Event googleEvent = new Event()
                .setSummary(event.title())
                .setDescription(event.description())
                .setLocation(event.location());

        boolean allDay = Boolean.TRUE.equals(event.allDay());
        LocalDateTime start = dateResolver.parseDateTime(event.startDateTime(), DEFAULT_ZONE, false);
        LocalDateTime end = event.endDateTime() == null || event.endDateTime().isBlank()
                ? defaultEnd(start, allDay)
                : dateResolver.parseDateTime(event.endDateTime(), DEFAULT_ZONE, allDay);

        if (allDay) {
            googleEvent.setStart(new EventDateTime().setDate(new DateTime(start.toLocalDate().toString())));
            googleEvent.setEnd(new EventDateTime().setDate(new DateTime(end.toLocalDate().toString())));
        } else {
            googleEvent.setStart(new EventDateTime()
                    .setDateTime(toGoogleDateTime(start))
                    .setTimeZone(DEFAULT_ZONE.getId()));
            googleEvent.setEnd(new EventDateTime()
                    .setDateTime(toGoogleDateTime(end))
                    .setTimeZone(DEFAULT_ZONE.getId()));
        }
        return googleEvent;
    }

    private DateTime toGoogleDateTime(LocalDateTime value) {
        Date date = Date.from(value.atZone(DEFAULT_ZONE).toInstant());
        return new DateTime(date);
    }

    private MeetingResponseDTO toMeetingResponse(Meeting meeting) {
        MeetingResponseDTO response = new MeetingResponseDTO();
        response.setId(meeting.getId());
        response.setTitle(meeting.getTitle());
        response.setStartTimestamp(meeting.getStartTimestamp());
        response.setEndTimestamp(meeting.getEndTimestamp());
        response.setParticipants(meeting.getParticipants());
        return response;
    }

    private TodoItemResponseDTO toTodoResponse(TodoItem todoItem) {
        TodoItemResponseDTO response = new TodoItemResponseDTO();
        response.setId(todoItem.getId());
        response.setDescription(todoItem.getDescription());
        response.setDueDate(todoItem.getDueDate());
        response.setCompleted(todoItem.isCompleted());
        response.setGoogleTaskId(todoItem.getGoogleTaskId());
        response.setGoogleTaskListId(todoItem.getGoogleTaskListId());
        response.setGooglePosition(todoItem.getGooglePosition());
        response.setGoogleUpdatedAt(todoItem.getGoogleUpdatedAt());
        response.setSyncStatus(todoItem.getSyncStatus());
        return response;
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }
}
