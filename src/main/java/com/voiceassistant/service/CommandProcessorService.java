package com.voiceassistant.service;

import com.voiceassistant.dto.MeetingRequestDTO;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.voiceassistant.service.TranscriptionService.extractEmailAddress;

@Service
public class CommandProcessorService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Stockholm");
    private static final Pattern CLOCK_TIME_PATTERN = Pattern.compile(
            "(?:\\bklockan\\b|\\bkl\\.?)\\s+([a-zåäö0-9]{1,8})(?:\\s*(?::|\\.)\\s*([a-zåäö0-9]{1,8})|\\s+([a-zåäö0-9]{1,8}))?");
    private static final Pattern RUSSIAN_DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s+(январ[ья]|феврал[ья]|март[а]?|апрел[ья]|ма[йя]|июн[ья]|июл[ья]|август[а]?|сентябр[ья]|октябр[ья]|ноябр[ья]|декабр[ья])\\s+(\\d{4})(?:\\s+года)?");
    private static final Pattern RUSSIAN_TIME_RANGE_PATTERN = Pattern.compile(
            "(?:^|\\s)с\\s+(\\d{1,2})(?:[:.]?\\s*(\\d{2}))?\\s+до\\s+(\\d{1,2})(?:[:.]?\\s*(\\d{2}))?(?:\\s|$)");
    private static final Map<String, Integer> SWEDISH_TIME_WORDS = Map.ofEntries(
            Map.entry("noll", 0),
            Map.entry("oll", 0),
            Map.entry("ett", 1),
            Map.entry("en", 1),
            Map.entry("två", 2),
            Map.entry("tva", 2),
            Map.entry("tre", 3),
            Map.entry("fyra", 4),
            Map.entry("fem", 5),
            Map.entry("sex", 6),
            Map.entry("sju", 7),
            Map.entry("åtta", 8),
            Map.entry("atta", 8),
            Map.entry("nio", 9),
            Map.entry("tio", 10),
            Map.entry("elva", 11),
            Map.entry("tolv", 12),
            Map.entry("tretton", 13),
            Map.entry("fjorton", 14),
            Map.entry("femton", 15),
            Map.entry("sexton", 16),
            Map.entry("sjutton", 17),
            Map.entry("arton", 18),
            Map.entry("nitton", 19),
            Map.entry("tjugo", 20),
            Map.entry("trettio", 30),
            Map.entry("fyrtio", 40),
            Map.entry("femtio", 50));
    private static final Map<String, DayOfWeek> SWEDISH_WEEKDAYS = Map.of(
            "måndag", DayOfWeek.MONDAY,
            "mandag", DayOfWeek.MONDAY,
            "tisdag", DayOfWeek.TUESDAY,
            "onsdag", DayOfWeek.WEDNESDAY,
            "torsdag", DayOfWeek.THURSDAY,
            "fredag", DayOfWeek.FRIDAY,
            "lördag", DayOfWeek.SATURDAY,
            "lordag", DayOfWeek.SATURDAY,
            "söndag", DayOfWeek.SUNDAY,
            "sondag", DayOfWeek.SUNDAY);
    private static final Map<String, Integer> RUSSIAN_MONTHS = Map.ofEntries(
            Map.entry("января", 1),
            Map.entry("январь", 1),
            Map.entry("февраля", 2),
            Map.entry("февраль", 2),
            Map.entry("марта", 3),
            Map.entry("март", 3),
            Map.entry("апреля", 4),
            Map.entry("апрель", 4),
            Map.entry("мая", 5),
            Map.entry("май", 5),
            Map.entry("июня", 6),
            Map.entry("июнь", 6),
            Map.entry("июля", 7),
            Map.entry("июль", 7),
            Map.entry("августа", 8),
            Map.entry("август", 8),
            Map.entry("сентября", 9),
            Map.entry("сентябрь", 9),
            Map.entry("октября", 10),
            Map.entry("октябрь", 10),
            Map.entry("ноября", 11),
            Map.entry("ноябрь", 11),
            Map.entry("декабря", 12),
            Map.entry("декабрь", 12));

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
        if (preview == null) {
            preview = new VoiceCommandPreviewDTO();
            preview.setType(VoiceCommandType.UNKNOWN);
            preview.setMessage("Kunde inte tolka kommandot");
        }
        if (preview.getType() == null || preview.getType() == VoiceCommandType.UNKNOWN) {
            preview = buildHeuristicVoicePreview(transcription).orElse(preview);
        }
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

    private Optional<VoiceCommandPreviewDTO> buildHeuristicVoicePreview(String transcription) {
        if (transcription == null || transcription.isBlank()) {
            return Optional.empty();
        }

        String normalized = transcription.toLowerCase(Locale.ROOT);
        if (!looksLikeCalendarEvent(normalized)) {
            return Optional.empty();
        }

        Optional<LocalDate> date = resolveVoiceCommandDate(normalized);
        Optional<TimeRange> timeRange = resolveVoiceCommandTimeRange(normalized);
        if (date.isEmpty() || timeRange.isEmpty()) {
            return Optional.empty();
        }

        LocalDateTime start = LocalDateTime.of(date.get(), timeRange.get().start());
        MeetingRequestDTO meeting = new MeetingRequestDTO();
        meeting.setTitle(resolveMeetingTitle(normalized));
        meeting.setStartTimestamp(start);
        meeting.setEndTimestamp(resolveEndTimestamp(date.get(), timeRange.get(), start));
        meeting.setParticipants(List.of(new Participants()));

        VoiceCommandPreviewDTO preview = new VoiceCommandPreviewDTO();
        preview.setType(VoiceCommandType.MEETING);
        preview.setMeeting(meeting);
        return Optional.of(preview);
    }

    private boolean looksLikeCalendarEvent(String normalized) {
        return normalized.contains("möte")
                || normalized.contains("mote")
                || normalized.contains("händelse")
                || normalized.contains("handelse")
                || normalized.contains("kalender")
                || normalized.contains("митинг")
                || normalized.contains("мете")
                || normalized.contains("встреч")
                || normalized.contains("календар")
                || (normalized.contains("gmail")
                        && (normalized.contains("skapa") || normalized.contains("lägg") || normalized.contains("lagg")));
    }

    private Optional<LocalDate> resolveVoiceCommandDate(String normalized) {
        Optional<LocalDate> russianDate = resolveRussianDate(normalized);
        return russianDate.isPresent() ? russianDate : resolveSwedishDate(normalized);
    }

    private Optional<TimeRange> resolveVoiceCommandTimeRange(String normalized) {
        Optional<TimeRange> russianTimeRange = resolveRussianTimeRange(normalized);
        if (russianTimeRange.isPresent()) {
            return russianTimeRange;
        }

        return resolveClockTime(normalized)
                .map(start -> new TimeRange(start, start.plusHours(1)));
    }

    private LocalDateTime resolveEndTimestamp(LocalDate date, TimeRange timeRange, LocalDateTime start) {
        return timeRange.end().isAfter(timeRange.start())
                ? LocalDateTime.of(date, timeRange.end())
                : start.plusHours(1);
    }

    private Optional<LocalDate> resolveRussianDate(String normalized) {
        Matcher matcher = RUSSIAN_DATE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }

        int day = Integer.parseInt(matcher.group(1));
        Integer month = RUSSIAN_MONTHS.get(matcher.group(2));
        int year = Integer.parseInt(matcher.group(3));
        if (month == null) {
            return Optional.empty();
        }

        return Optional.of(LocalDate.of(year, month, day));
    }

    private Optional<TimeRange> resolveRussianTimeRange(String normalized) {
        Matcher matcher = RUSSIAN_TIME_RANGE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }

        Optional<LocalTime> start = parseNumericTime(matcher.group(1), matcher.group(2));
        Optional<LocalTime> end = parseNumericTime(matcher.group(3), matcher.group(4));
        if (start.isEmpty() || end.isEmpty() || !end.get().isAfter(start.get())) {
            return Optional.empty();
        }

        return Optional.of(new TimeRange(start.get(), end.get()));
    }

    private Optional<LocalTime> parseNumericTime(String hourValue, String minuteValue) {
        try {
            int hour = Integer.parseInt(hourValue);
            int minute = minuteValue == null || minuteValue.isBlank() ? 0 : Integer.parseInt(minuteValue);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return Optional.empty();
            }
            return Optional.of(LocalTime.of(hour, minute));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<LocalDate> resolveSwedishDate(String normalized) {
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        if (normalized.contains("övermorgon") || normalized.contains("overmorgon")) {
            return Optional.of(today.plusDays(2));
        }
        if (normalized.contains("i morgon") || normalized.contains("imorgon")) {
            return Optional.of(today.plusDays(1));
        }
        if (normalized.contains("idag")) {
            return Optional.of(today);
        }

        for (Map.Entry<String, DayOfWeek> weekday : SWEDISH_WEEKDAYS.entrySet()) {
            if (normalized.contains(weekday.getKey())) {
                return Optional.of(today.with(TemporalAdjusters.next(weekday.getValue())));
            }
        }

        return Optional.empty();
    }

    private Optional<LocalTime> resolveClockTime(String normalized) {
        Matcher matcher = CLOCK_TIME_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }

        OptionalInt hour = parseTimeToken(matcher.group(1));
        if (hour.isEmpty() || hour.getAsInt() < 0 || hour.getAsInt() > 23) {
            return Optional.empty();
        }

        int minute = 0;
        String minuteToken = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        OptionalInt parsedMinute = parseTimeToken(minuteToken);
        if (parsedMinute.isPresent() && parsedMinute.getAsInt() >= 0 && parsedMinute.getAsInt() <= 59) {
            minute = parsedMinute.getAsInt();
        }

        return Optional.of(LocalTime.of(hour.getAsInt(), minute));
    }

    private OptionalInt parseTimeToken(String value) {
        if (value == null || value.isBlank()) {
            return OptionalInt.empty();
        }

        String token = value.trim().toLowerCase(Locale.ROOT);
        try {
            return OptionalInt.of(Integer.parseInt(token));
        } catch (NumberFormatException ignored) {
            Integer wordValue = SWEDISH_TIME_WORDS.get(token);
            return wordValue == null ? OptionalInt.empty() : OptionalInt.of(wordValue);
        }
    }

    private String resolveMeetingTitle(String normalized) {
        if (normalized.contains("möte") || normalized.contains("mote")) {
            return "Möte";
        }
        if (normalized.contains("тонировка машины")) {
            return "Тонировка машины";
        }
        if (normalized.contains("митинг") || normalized.contains("мете") || normalized.contains("встреч")) {
            return "Митинг";
        }
        return "Händelse";
    }

    private record TimeRange(LocalTime start, LocalTime end) {
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
