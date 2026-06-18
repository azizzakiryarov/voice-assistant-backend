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

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class TextAnalysisPostProcessor {

    private final TextAnalysisDateResolver dateResolver;
    private final TextAnalysisUrgencyResolver urgencyResolver;

    public TextAnalysisPostProcessor(TextAnalysisDateResolver dateResolver, TextAnalysisUrgencyResolver urgencyResolver) {
        this.dateResolver = dateResolver;
        this.urgencyResolver = urgencyResolver;
    }

    public TextAnalysisResponseDTO normalize(TextAnalysisResponseDTO response, OffsetDateTime receivedAt, ZoneId zone) {
        List<TextAnalysisWarningDTO> warnings = new ArrayList<>(safeList(response.warnings()));
        List<TextAnalysisEventDTO> events = deduplicateEvents(safeList(response.events()).stream()
                .filter(event -> hasText(event.title()) && hasText(event.startDateTime()))
                .map(this::normalizeEvent)
                .toList());

        List<TextAnalysisTodoDTO> todos = deduplicateTodos(safeList(response.todos()).stream()
                .filter(todo -> hasText(todo.title()))
                .map(todo -> normalizeTodo(todo, warnings))
                .toList());

        events.forEach(event -> addDateWarnings(event, receivedAt, zone, warnings));
        todos.forEach(todo -> addDeadlineWarnings(todo, receivedAt, zone, warnings));

        return new TextAnalysisResponseDTO(
                blankToDefault(response.summary(), "Texten har analyserats."),
                blankToDefault(response.language(), "unknown"),
                events,
                todos,
                deduplicateInformationalItems(safeList(response.informationalItems()).stream()
                        .filter(item -> hasText(item.title()))
                        .map(this::normalizeInformationalItem)
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
            List<TextAnalysisWarningDTO> warnings) {
        if (!dateResolver.sourceContainsExplicitYear(event.sourceText()) && containsYear(event.startDateTime())) {
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
        if (!dateResolver.sourceContainsExplicitYear(todo.sourceText()) && containsYear(todo.deadline())) {
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
