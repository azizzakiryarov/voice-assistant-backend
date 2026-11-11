package com.voiceassistant.service;

import com.voiceassistant.dto.DeadlineType;
import com.voiceassistant.dto.ItemCategory;
import com.voiceassistant.dto.TextAnalysisEventDTO;
import com.voiceassistant.dto.TextAnalysisInformationalItemDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.dto.TextAnalysisTodoDTO;
import com.voiceassistant.dto.TextAnalysisWarningDTO;
import com.voiceassistant.dto.Urgency;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextAnalysisPostProcessor {

    // "Längd på passet: 90 minuter", "pågår i 1,5 timme".
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "\\b(\\d+(?:[.,]\\d+)?)\\s*(minuter|min|minutes|timmar|timme|tim|hours|hour|h)\\b",
            Pattern.CASE_INSENSITIVE);

    private final TextAnalysisDateResolver dateResolver;
    private final TextAnalysisUrgencyResolver urgencyResolver;

    public TextAnalysisPostProcessor(TextAnalysisDateResolver dateResolver, TextAnalysisUrgencyResolver urgencyResolver) {
        this.dateResolver = dateResolver;
        this.urgencyResolver = urgencyResolver;
    }

    public TextAnalysisResponseDTO normalize(TextAnalysisResponseDTO response, OffsetDateTime receivedAt, ZoneId zone) {
        return normalize(response, receivedAt, zone, null);
    }

    /**
     * @param inputText the text the user analysed, used to check that a year really is missing and to
     *                  read a duration such as "90 minuter"; may be null
     */
    public TextAnalysisResponseDTO normalize(
            TextAnalysisResponseDTO response,
            OffsetDateTime receivedAt,
            ZoneId zone,
            String inputText) {
        List<TextAnalysisWarningDTO> warnings = new ArrayList<>(safeList(response.warnings()));
        List<TextAnalysisEventDTO> events = fillEndFromDuration(deduplicateEventsByTitleAndStart(deduplicateEvents(
                safeList(response.events()).stream()
                        .filter(event -> hasText(event.title()) && hasText(event.startDateTime()))
                        .map(this::normalizeEvent)
                        .toList())), inputText);

        List<TextAnalysisTodoDTO> todos = deduplicateTodos(safeList(response.todos()).stream()
                .filter(todo -> hasText(todo.title()))
                .map(todo -> normalizeTodo(todo, warnings))
                .filter(todo -> !repeatsEvent(todo, events))
                .toList());

        events.forEach(event -> addDateWarnings(event, receivedAt, zone, inputText, warnings));
        todos.forEach(todo -> addDeadlineWarnings(todo, receivedAt, zone, inputText, warnings));

        return new TextAnalysisResponseDTO(
                blankToDefault(response.summary(), "Texten har analyserats."),
                blankToDefault(response.language(), "unknown"),
                events,
                todos,
                deduplicateInformationalItems(safeList(response.informationalItems()).stream()
                        .filter(item -> hasText(item.title()))
                        .map(this::normalizeInformationalItem)
                        .filter(item -> events.stream().noneMatch(event -> sameTitle(item.title(), event.title())))
                        .toList()),
                deduplicateWarnings(warnings)
        );
    }

    List<TextAnalysisEventDTO> deduplicateEvents(List<TextAnalysisEventDTO> events) {
        Map<String, TextAnalysisEventDTO> deduplicated = new LinkedHashMap<>();
        for (TextAnalysisEventDTO event : events) {
            deduplicated.putIfAbsent(eventFingerprint(event), event);
        }
        return List.copyOf(deduplicated.values());
    }

    // The model words description and quote differently each time, so the same event can slip through
    // deduplicateEvents. The same title at the same minute is the same event; keep the most complete one.
    List<TextAnalysisEventDTO> deduplicateEventsByTitleAndStart(List<TextAnalysisEventDTO> events) {
        Map<String, TextAnalysisEventDTO> deduplicated = new LinkedHashMap<>();
        for (TextAnalysisEventDTO event : events) {
            String key = plainText(event.title()) + "|" + minuteKey(event.startDateTime());
            TextAnalysisEventDTO existing = deduplicated.get(key);
            if (existing == null) {
                deduplicated.put(key, event);
            } else if (existing.endDateTime() == null && event.endDateTime() != null) {
                deduplicated.put(key, withEnd(existing, event.endDateTime()));
            }
        }
        return List.copyOf(deduplicated.values());
    }

    // A todo with the same title as an event, or a deadline at the moment an event starts, only repeats it.
    private boolean repeatsEvent(TextAnalysisTodoDTO todo, List<TextAnalysisEventDTO> events) {
        return events.stream().anyMatch(event -> sameTitle(todo.title(), event.title())
                || (todo.deadline() != null && minuteKey(todo.deadline()).equals(minuteKey(event.startDateTime()))));
    }

    // Sets the end of the only timed event without one when the text states exactly one duration.
    private List<TextAnalysisEventDTO> fillEndFromDuration(List<TextAnalysisEventDTO> events, String inputText) {
        List<TextAnalysisEventDTO> missingEnd = events.stream()
                .filter(event -> event.endDateTime() == null && !event.allDay() && event.startDateTime().contains("T"))
                .toList();
        if (missingEnd.size() != 1 || inputText == null) {
            return events;
        }
        Matcher matcher = DURATION_PATTERN.matcher(inputText);
        if (!matcher.find()) {
            return events;
        }
        double amount = Double.parseDouble(matcher.group(1).replace(',', '.'));
        long minutes = Math.round(matcher.group(2).toLowerCase(Locale.ROOT).startsWith("m") ? amount : amount * 60);
        if (matcher.find() || minutes <= 0 || minutes > 24 * 60) {
            return events;
        }

        TextAnalysisEventDTO target = missingEnd.getFirst();
        String end = plusMinutes(target.startDateTime(), minutes);
        if (end == null) {
            return events;
        }
        return events.stream().map(event -> event == target ? withEnd(event, end) : event).toList();
    }

    private static String plusMinutes(String dateTime, long minutes) {
        try {
            return OffsetDateTime.parse(dateTime).plusMinutes(minutes).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException notOffset) {
            try {
                return LocalDateTime.parse(dateTime).plusMinutes(minutes).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException unreadable) {
                return null;
            }
        }
    }

    private static TextAnalysisEventDTO withEnd(TextAnalysisEventDTO event, String end) {
        return new TextAnalysisEventDTO(event.title(), event.description(), event.startDateTime(), end, event.allDay(),
                event.location(), event.category(), event.urgency(), event.confidence(), event.requiresConfirmation(),
                event.sourceText());
    }

    // Exact wording only: canonicalText maps related items to one token ("junior-club"), which is
    // right for bilingual duplicates but would treat a price note as a repeat of the trial event.
    // An item repeats an event when its title is the event's title or a part of it, such as
    // "Sparring all levels" in "Bokning för Sparring all levels". Short titles must match exactly.
    private boolean sameTitle(String item, String event) {
        String itemText = plainText(item);
        String eventText = plainText(event);
        return itemText.equals(eventText) || (itemText.length() >= 12 && eventText.contains(itemText));
    }

    private static String plainText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String minuteKey(String dateTime) {
        return dateTime.length() >= 16 ? dateTime.substring(0, 16) : dateTime;
    }

    List<TextAnalysisTodoDTO> deduplicateTodos(List<TextAnalysisTodoDTO> todos) {
        Map<String, TextAnalysisTodoDTO> deduplicated = new LinkedHashMap<>();
        for (TextAnalysisTodoDTO todo : todos) {
            deduplicated.putIfAbsent(todoFingerprint(todo), todo);
        }
        return List.copyOf(deduplicated.values());
    }

    private TextAnalysisEventDTO normalizeEvent(TextAnalysisEventDTO event) {
        return new TextAnalysisEventDTO(
                event.title().trim(),
                blankToNull(event.description()),
                blankToNull(event.startDateTime()),
                blankToNull(event.endDateTime()),
                Boolean.TRUE.equals(event.allDay()),
                blankToNull(event.location()),
                event.category() == null ? ItemCategory.OTHER : event.category(),
                event.urgency() == null ? Urgency.LOW : event.urgency(),
                clampConfidence(event.confidence()),
                event.requiresConfirmation() == null || event.requiresConfirmation(),
                blankToNull(event.sourceText())
        );
    }

    private TextAnalysisTodoDTO normalizeTodo(TextAnalysisTodoDTO todo, List<TextAnalysisWarningDTO> warnings) {
        DeadlineType deadlineType = todo.deadlineType() == null ? DeadlineType.UNKNOWN : todo.deadlineType();
        String deadline = blankToNull(todo.deadline());
        if (deadline != null && !dateResolver.exactDeadlineAllowed(deadlineType)) {
            warnings.add(new TextAnalysisWarningDTO(
                    "IGNORED_EXACT_DEADLINE",
                    "En exakt deadline ignorerades eftersom deadline-typen är " + deadlineType + ".",
                    true));
            deadline = null;
        }
        return new TextAnalysisTodoDTO(
                todo.title().trim(),
                blankToNull(todo.description()),
                deadline,
                deadlineType,
                todo.recurrence(),
                todo.category() == null ? ItemCategory.OTHER : todo.category(),
                urgencyResolver.resolveTodoUrgency(todo.urgency(), deadlineType, todo.category(), todo.sourceText()),
                clampConfidence(todo.confidence()),
                todo.requiresConfirmation() == null || todo.requiresConfirmation(),
                blankToNull(todo.sourceText())
        );
    }

    private TextAnalysisInformationalItemDTO normalizeInformationalItem(TextAnalysisInformationalItemDTO item) {
        return new TextAnalysisInformationalItemDTO(
                item.title().trim(),
                blankToNull(item.description()),
                item.category() == null ? ItemCategory.OTHER : item.category(),
                blankToNull(item.sourceText())
        );
    }

    private void addDateWarnings(
            TextAnalysisEventDTO event,
            OffsetDateTime receivedAt,
            ZoneId zone,
            String inputText,
            List<TextAnalysisWarningDTO> warnings) {
        if (yearInferred(event.sourceText(), event.startDateTime(), inputText)) {
            addWarningIfMissing(
                    warnings,
                    "YEAR_INFERRED",
                    "Årtalet anges inte uttryckligen för \"" + event.title() + "\" och behöver kontrolleras.");
        }
        try {
            if (dateResolver.parseDate(event.startDateTime(), zone).isBefore(receivedAt.atZoneSameInstant(zone).toLocalDate())) {
                warnings.add(new TextAnalysisWarningDTO(
                        "DATE_IN_PAST",
                        "Datumet för \"" + event.title() + "\" har redan passerat.",
                        true));
            }
        } catch (RuntimeException ignored) {
            warnings.add(new TextAnalysisWarningDTO(
                    "INVALID_EVENT_DATE",
                    "Datumet för \"" + event.title() + "\" kunde inte valideras.",
                    true));
        }
    }

    private void addDeadlineWarnings(
            TextAnalysisTodoDTO todo,
            OffsetDateTime receivedAt,
            ZoneId zone,
            String inputText,
            List<TextAnalysisWarningDTO> warnings) {
        if (todo.deadline() == null) {
            if (todo.deadlineType() == DeadlineType.AS_SOON_AS_POSSIBLE
                    || todo.deadlineType() == DeadlineType.EARLIEST_CONVENIENCE) {
                addWarningIfMissing(
                        warnings,
                        "MISSING_EXACT_DEADLINE",
                        "\"" + todo.title() + "\" saknar ett exakt sista datum.");
            }
            return;
        }
        if (yearInferred(todo.sourceText(), todo.deadline(), inputText)) {
            addWarningIfMissing(
                    warnings,
                    "YEAR_INFERRED",
                    "Årtalet anges inte uttryckligen för \"" + todo.title() + "\" och behöver kontrolleras.");
        }
        try {
            if (dateResolver.parseDate(todo.deadline(), zone).isBefore(receivedAt.atZoneSameInstant(zone).toLocalDate())) {
                warnings.add(new TextAnalysisWarningDTO(
                        "DEADLINE_IN_PAST",
                        "Deadlinen för \"" + todo.title() + "\" har redan passerat.",
                        true));
            }
        } catch (RuntimeException ignored) {
            warnings.add(new TextAnalysisWarningDTO(
                    "INVALID_TODO_DEADLINE",
                    "Deadlinen för \"" + todo.title() + "\" kunde inte valideras.",
                    true));
        }
    }

    private List<TextAnalysisInformationalItemDTO> deduplicateInformationalItems(List<TextAnalysisInformationalItemDTO> items) {
        Map<String, TextAnalysisInformationalItemDTO> deduplicated = new LinkedHashMap<>();
        for (TextAnalysisInformationalItemDTO item : items) {
            deduplicated.putIfAbsent(canonicalText(item.title() + " " + item.description()), item);
        }
        return List.copyOf(deduplicated.values());
    }

    private List<TextAnalysisWarningDTO> deduplicateWarnings(List<TextAnalysisWarningDTO> warnings) {
        Map<String, TextAnalysisWarningDTO> deduplicated = new LinkedHashMap<>();
        for (TextAnalysisWarningDTO warning : warnings) {
            if (warning == null || !hasText(warning.code())) {
                continue;
            }
            String key = warning.code() + "|" + blankToDefault(warning.message(), "");
            deduplicated.putIfAbsent(key, new TextAnalysisWarningDTO(
                    warning.code(),
                    blankToDefault(warning.message(), warning.code()),
                    warning.requiresUserAttention() == null || warning.requiresUserAttention()));
        }
        return List.copyOf(deduplicated.values());
    }

    private void addWarningIfMissing(List<TextAnalysisWarningDTO> warnings, String code, String message) {
        boolean exists = warnings.stream()
                .filter(Objects::nonNull)
                .anyMatch(warning -> code.equals(warning.code()));
        if (!exists) {
            warnings.add(new TextAnalysisWarningDTO(code, message, true));
        }
    }

    private String eventFingerprint(TextAnalysisEventDTO event) {
        String dateKey = blankToDefault(event.startDateTime(), "") + "|" + blankToDefault(event.endDateTime(), "");
        String contextKey = canonicalText(event.title() + " " + event.description() + " " + event.location() + " " + event.sourceText());
        return event.category() + "|" + dateKey + "|" + contextKey;
    }

    private String todoFingerprint(TextAnalysisTodoDTO todo) {
        String contextKey = canonicalText(todo.title() + " " + todo.description() + " " + todo.sourceText());
        if (isSemanticDedupKey(contextKey)) {
            return todo.category() + "|" + contextKey;
        }
        return todo.category() + "|" + todo.deadlineType() + "|" + blankToDefault(todo.deadline(), "") + "|" + contextKey;
    }

    private boolean isSemanticDedupKey(String contextKey) {
        return contextKey.contains("special-diet")
                || contextKey.contains("mother-tongue")
                || contextKey.contains("schoolsoft");
    }

    private String canonicalText(String value) {
        String normalized = blankToDefault(value, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        List<String> tokens = new ArrayList<>();
        addCanonicalToken(tokens, normalized, "special-diet", "specialkost", "special diet", "dietary", "allerg");
        addCanonicalToken(tokens, normalized, "mother-tongue", "modersmål", "mother tongue", "native language", "home language");
        addCanonicalToken(tokens, normalized, "schoolsoft", "schoolsoft");
        addCanonicalToken(tokens, normalized, "junior-club", "junior club");
        addCanonicalToken(tokens, normalized, "first-school-day", "första skoldagen", "first school day", "first day of school");
        addCanonicalToken(tokens, normalized, "regular-schedule", "ordinarie schema", "regular schedule", "regular timetable");
        if (!tokens.isEmpty()) {
            return String.join("+", tokens);
        }
        return normalized;
    }

    private void addCanonicalToken(List<String> tokens, String value, String token, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                tokens.add(token);
                return;
            }
        }
    }

    // The quote from the model is short, so also look for the year in the whole text.
    private boolean yearInferred(String sourceText, String dateTime, String inputText) {
        if (!containsYear(dateTime) || dateResolver.sourceContainsExplicitYear(sourceText)) {
            return false;
        }
        return inputText == null || !inputText.contains(dateTime.substring(0, 4));
    }

    private boolean containsYear(String value) {
        return value != null && value.matches(".*\\b(?:19|20)\\d{2}\\b.*");
    }

    private Double clampConfidence(Double confidence) {
        if (confidence == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String blankToDefault(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value.stream().filter(Objects::nonNull).toList();
    }
}
