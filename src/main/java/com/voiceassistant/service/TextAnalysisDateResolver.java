package com.voiceassistant.service;

import com.voiceassistant.dto.DeadlineType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Component
public class TextAnalysisDateResolver {

    private static final Pattern EXPLICIT_YEAR_PATTERN = Pattern.compile("\\b(?:19|20)\\d{2}\\b");

    public LocalDate resolveMonthDay(MonthDay monthDay, OffsetDateTime receivedAt, ZoneId zone) {
        LocalDate receivedDate = receivedAt.atZoneSameInstant(zone).toLocalDate();
        LocalDate candidate = monthDay.atYear(receivedDate.getYear());
        if (candidate.isBefore(receivedDate)) {
            return monthDay.atYear(receivedDate.getYear() + 1);
        }
        return candidate;
    }

    public boolean sourceContainsExplicitYear(String sourceText) {
        return sourceText != null && EXPLICIT_YEAR_PATTERN.matcher(sourceText).find();
    }

    public boolean exactDeadlineAllowed(DeadlineType deadlineType) {
        return deadlineType == DeadlineType.EXACT_DATE || deadlineType == DeadlineType.EXACT_DATE_TIME;
    }

    public LocalDate parseDate(String value, ZoneId zone) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Try richer ISO-8601 formats below.
        }
        try {
            return OffsetDateTime.parse(trimmed).atZoneSameInstant(zone).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // Try local date-time below.
        }
        return LocalDateTime.parse(trimmed).toLocalDate();
    }

    public LocalDateTime parseDateTime(String value, ZoneId zone, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed).atZoneSameInstant(zone).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try local date-time below.
        }
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Try all-day date below.
        }
        LocalDate date = LocalDate.parse(trimmed);
        return endOfDay ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
    }
}
