package com.voiceassistant.service;

import com.voiceassistant.dto.DeadlineType;
import com.voiceassistant.dto.ItemCategory;
import com.voiceassistant.dto.RecurrenceFrequency;
import com.voiceassistant.dto.TextAnalysisEventDTO;
import com.voiceassistant.dto.TextAnalysisInformationalItemDTO;
import com.voiceassistant.dto.TextAnalysisRecurrenceDTO;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.dto.TextAnalysisTodoDTO;
import com.voiceassistant.dto.TextAnalysisWarningDTO;
import com.voiceassistant.dto.Urgency;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextAnalysisHeuristicExtractor {

    private static final Pattern FIRST_SCHOOL_DAY_SV = Pattern.compile(
            "(?is)(första skoldagen är den\\s+(\\d{1,2})\\s+augusti\\s+kl\\.\\s*(\\d{1,2})[:.](\\d{2})\\s*[–-]\\s*(\\d{1,2})[:.](\\d{2}))");
    private static final Pattern FIRST_SCHOOL_DAY_EN = Pattern.compile(
            "(?is)(first day of school:?\\s+august\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s*,?\\s*(\\d{1,2})[:.](\\d{2})\\s*[–-]\\s*(\\d{1,2})[:.](\\d{2}))");
    private static final Pattern REGULAR_SCHEDULE = Pattern.compile(
            "(?is)((?:det ordinarie schemat börjar den\\s+(\\d{1,2})\\s+augusti)|(?:the regular timetable begins on august\\s+(\\d{1,2})(?:st|nd|rd|th)?))");
    private static final Pattern JUNIOR_CLUB_TRIAL = Pattern.compile(
            "(?is)((?:kostnadsfritt mellan den\\s+(\\d{1,2})\\s+och\\s+(\\d{1,2})\\s+augusti)|(?:free of charge between august\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+and\\s+august\\s+(\\d{1,2})(?:st|nd|rd|th)?))");
    private static final Pattern JUNIOR_CLUB_COST = Pattern.compile(
            "(?is)((?:kostnaden är därefter\\s+3\\s*500\\s*kr per termin[^.]*\\.)|(?:cost is\\s+3,?500\\s*sek per semester[^.]*\\.))");

    private final TextAnalysisDateResolver dateResolver;

    public TextAnalysisHeuristicExtractor(TextAnalysisDateResolver dateResolver) {
        this.dateResolver = dateResolver;
    }

    public TextAnalysisResponseDTO extract(TextAnalysisRequestDTO request) {
        String text = request.text() == null ? "" : request.text();
        String normalized = text.toLowerCase(Locale.ROOT);
        ZoneId zone = parseZone(request.timeZone());
        OffsetDateTime receivedAt = request.receivedAt() == null ? OffsetDateTime.now(zone) : request.receivedAt();

        List<TextAnalysisEventDTO> events = new ArrayList<>();
        List<TextAnalysisTodoDTO> todos = new ArrayList<>();
        List<TextAnalysisInformationalItemDTO> informationalItems = new ArrayList<>();
        List<TextAnalysisWarningDTO> warnings = new ArrayList<>();

        addFirstSchoolDay(text, events, zone, receivedAt);
        addRegularSchedule(text, events, zone, receivedAt);
        addJuniorClubTrial(text, events, zone, receivedAt);
        addSpecialDietTodo(normalized, todos);
        addHomeLanguageTodo(normalized, todos);
        addSchoolSoftTodo(normalized, todos);
        addJuniorClubCostInfo(text, normalized, informationalItems);

        if (!events.isEmpty()) {
            warnings.add(new TextAnalysisWarningDTO(
                    "YEAR_INFERRED",
                    "Årtal saknas i delar av texten och har härletts från mottagningsdatumet.",
                    true));
        }
        if (todos.stream().anyMatch(todo -> todo.deadline() == null
                && (todo.deadlineType() == DeadlineType.AS_SOON_AS_POSSIBLE
                || todo.deadlineType() == DeadlineType.EARLIEST_CONVENIENCE))) {
            warnings.add(new TextAnalysisWarningDTO(
                    "MISSING_EXACT_DEADLINE",
                    "En eller flera uppgifter saknar exakt sista datum.",
                    true));
        }

        return new TextAnalysisResponseDTO(
                hasIesSchoolStartContext(normalized)
                        ? "Information om skolstarten för årskurs 4 på IES Enskede."
                        : null,
                normalized.contains("dear parents") ? "mixed" : "sv",
                events,
                todos,
                informationalItems,
                warnings);
    }

    private boolean hasIesSchoolStartContext(String normalized) {
        return normalized.contains("ies enskede")
                || normalized.contains("internationella engelska skolan");
    }

    private void addFirstSchoolDay(String text, List<TextAnalysisEventDTO> events, ZoneId zone, OffsetDateTime receivedAt) {
        Matcher sv = FIRST_SCHOOL_DAY_SV.matcher(text);
        if (sv.find()) {
            addFirstSchoolDayEvent(events, sv.group(1), sv.group(2), sv.group(3), sv.group(4), sv.group(5), sv.group(6), zone, receivedAt);
            return;
        }
        Matcher en = FIRST_SCHOOL_DAY_EN.matcher(text);
        if (en.find()) {
            addFirstSchoolDayEvent(events, en.group(1), en.group(2), en.group(3), en.group(4), en.group(5), en.group(6), zone, receivedAt);
        }
    }

    private void addFirstSchoolDayEvent(
            List<TextAnalysisEventDTO> events,
            String sourceText,
            String day,
            String startHour,
            String startMinute,
            String endHour,
            String endMinute,
            ZoneId zone,
            OffsetDateTime receivedAt) {
        LocalDate date = resolveAugustDate(day, receivedAt, zone);
        events.add(new TextAnalysisEventDTO(
                "Första skoldagen på IES Enskede",
                "Samling för att träffa nya klasser och mentorer.",
                atOffset(date, startHour, startMinute, zone),
                atOffset(date, endHour, endMinute, zone),
                false,
                "Junior Schools lekplats, IES Enskede",
                ItemCategory.SCHOOL,
                Urgency.MEDIUM,
                0.98,
                true,
                compact(sourceText)));
    }

    private void addRegularSchedule(String text, List<TextAnalysisEventDTO> events, ZoneId zone, OffsetDateTime receivedAt) {
        Matcher matcher = REGULAR_SCHEDULE.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String day = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        LocalDate date = resolveAugustDate(day, receivedAt, zone);
        events.add(new TextAnalysisEventDTO(
                "Ordinarie skolschema börjar",
                "Det ordinarie schemat för årskurs 4 börjar.",
                date.toString(),
                null,
                true,
                null,
                ItemCategory.SCHOOL,
                Urgency.LOW,
                0.95,
                true,
                compact(matcher.group(1))));
    }

    private void addJuniorClubTrial(String text, List<TextAnalysisEventDTO> events, ZoneId zone, OffsetDateTime receivedAt) {
        Matcher matcher = JUNIOR_CLUB_TRIAL.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String startDay = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
        String endDay = matcher.group(3) != null ? matcher.group(3) : matcher.group(5);
        LocalDate start = resolveAugustDate(startDay, receivedAt, zone);
        LocalDate end = resolveAugustDate(endDay, receivedAt, zone);
        events.add(new TextAnalysisEventDTO(
                "Gratis provperiod för Junior Club",
                "Årskurs 4 kan prova Junior Club kostnadsfritt.",
                start.toString(),
                end.toString(),
                true,
                "IES Enskede",
                ItemCategory.SCHOOL,
                Urgency.LOW,
                0.96,
                true,
                compact(matcher.group(1))));
    }

    private void addSpecialDietTodo(String normalized, List<TextAnalysisTodoDTO> todos) {
        if (!normalized.contains("specialkost") && !normalized.contains("special dietary")) {
            return;
        }
        todos.add(new TextAnalysisTodoDTO(
                "Fyll i formuläret för specialkost",
                "Fyll i formuläret om barnet behöver specialkost eller har allergier.",
                null,
                DeadlineType.AS_SOON_AS_POSSIBLE,
                null,
                ItemCategory.SCHOOL,
                Urgency.HIGH,
                0.97,
                true,
                "Fyll i formuläret för specialkost så snart som möjligt."));
    }

    private void addHomeLanguageTodo(String normalized, List<TextAnalysisTodoDTO> todos) {
        if (!normalized.contains("modersmål") && !normalized.contains("home language")) {
            return;
        }
        todos.add(new TextAnalysisTodoDTO(
                "Skicka in blankett för modersmålsundervisning",
                "Fyll i och skicka tillbaka blanketten om barnet ska läsa modersmål.",
                null,
                DeadlineType.AS_SOON_AS_POSSIBLE,
                null,
                ItemCategory.SCHOOL,
                Urgency.MEDIUM,
                0.95,
                true,
                "Fyll i bifogat dokument och skicka tillbaka till oss så snart som möjligt."));
    }

    private void addSchoolSoftTodo(String normalized, List<TextAnalysisTodoDTO> todos) {
        if (!normalized.contains("schoolsoft") || !(normalized.contains("regelbundet") || normalized.contains("regularly"))) {
            return;
        }
        todos.add(new TextAnalysisTodoDTO(
                "Kontrollera SchoolSoft regelbundet",
                "Logga in i SchoolSoft för information från lärare och skoladministration.",
                null,
                DeadlineType.RECURRING,
                new TextAnalysisRecurrenceDTO(RecurrenceFrequency.WEEKLY, 1),
                ItemCategory.SCHOOL,
                Urgency.LOW,
                0.85,
                true,
                "Vänligen logga in regelbundet."));
    }

    private void addJuniorClubCostInfo(
            String text,
            String normalized,
            List<TextAnalysisInformationalItemDTO> informationalItems) {
        if (!normalized.contains("junior club") || !(normalized.contains("3 500") || normalized.contains("3,500"))) {
            return;
        }
        Matcher matcher = JUNIOR_CLUB_COST.matcher(text);
        String sourceText = matcher.find()
                ? matcher.group(1)
                : "Kostnaden är därefter 3 500 kr per termin.";
        informationalItems.add(new TextAnalysisInformationalItemDTO(
                "Junior Club kostar 3 500 kr per termin",
                "Avgiften efter provperioden är 3 500 SEK per termin och återbetalas inte.",
                ItemCategory.SCHOOL,
                compact(sourceText)));
    }

    private ZoneId parseZone(String value) {
        if (value == null || value.isBlank()) {
            return TextAnalysisService.DEFAULT_ZONE;
        }
        return ZoneId.of(value);
    }

    private LocalDate resolveAugustDate(String day, OffsetDateTime receivedAt, ZoneId zone) {
        return dateResolver.resolveMonthDay(MonthDay.of(8, Integer.parseInt(day)), receivedAt, zone);
    }

    private String atOffset(LocalDate date, String hour, String minute, ZoneId zone) {
        LocalTime time = LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute));
        return date.atTime(time).atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String compact(String value) {
        if (value == null) {
            return null;
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        return compacted.length() <= 120 ? compacted : compacted.substring(0, 117) + "...";
    }
}
