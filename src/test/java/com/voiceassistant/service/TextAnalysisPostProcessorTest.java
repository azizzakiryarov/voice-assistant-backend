package com.voiceassistant.service;

import com.voiceassistant.dto.DeadlineType;
import com.voiceassistant.dto.ItemCategory;
import com.voiceassistant.dto.TextAnalysisEventDTO;
import com.voiceassistant.dto.TextAnalysisInformationalItemDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.dto.TextAnalysisTodoDTO;
import com.voiceassistant.dto.TextAnalysisWarningDTO;
import com.voiceassistant.dto.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextAnalysisPostProcessorTest {

    private TextAnalysisPostProcessor postProcessor;

    @BeforeEach
    void setUp() {
        postProcessor = new TextAnalysisPostProcessor(new TextAnalysisDateResolver(), new TextAnalysisUrgencyResolver());
    }

    @Test
    void deduplicatesSwedishAndEnglishVersionsOfSameSchoolEvent() {
        TextAnalysisResponseDTO response = new TextAnalysisResponseDTO(
                "Skolstart",
                "mixed",
                List.of(
                        new TextAnalysisEventDTO(
                                "Första skoldagen på IES Enskede",
                                "Samling på Junior Schools lekplats",
                                "2026-08-17T08:30:00+02:00",
                                "2026-08-17T10:30:00+02:00",
                                false,
                                "Junior Schools lekplats",
                                ItemCategory.SCHOOL,
                                Urgency.MEDIUM,
                                0.98,
                                true,
                                "Första skoldagen är den 17 augusti kl. 08:30–10:30"),
                        new TextAnalysisEventDTO(
                                "First school day at IES Enskede",
                                "Meet at the Junior School playground",
                                "2026-08-17T08:30:00+02:00",
                                "2026-08-17T10:30:00+02:00",
                                false,
                                "Junior Schools lekplats",
                                ItemCategory.SCHOOL,
                                Urgency.MEDIUM,
                                0.98,
                                true,
                                "The first school day is 17 August at 08:30-10:30")),
                List.of(),
                List.of(),
                List.of());

        TextAnalysisResponseDTO normalized = postProcessor.normalize(
                response,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                ZoneId.of("Europe/Stockholm"));

        assertThat(normalized.events()).hasSize(1);
    }

    @Test
    void keepsAsSoonAsPossibleDeadlineWithoutInventingExactDate() {
        TextAnalysisResponseDTO response = new TextAnalysisResponseDTO(
                "Specialkost",
                "sv",
                List.of(),
                List.of(new TextAnalysisTodoDTO(
                        "Fyll i formuläret för specialkost",
                        "Barnet behöver specialkost eller har allergier.",
                        "2026-06-18",
                        DeadlineType.AS_SOON_AS_POSSIBLE,
                        null,
                        ItemCategory.SCHOOL,
                        Urgency.LOW,
                        0.97,
                        true,
                        "Vi ber er att fylla i formuläret för specialkost så snart som möjligt.")),
                List.of(),
                List.of());

        TextAnalysisResponseDTO normalized = postProcessor.normalize(
                response,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                ZoneId.of("Europe/Stockholm"));

        assertThat(normalized.todos()).hasSize(1);
        assertThat(normalized.todos().getFirst().deadline()).isNull();
        assertThat(normalized.todos().getFirst().urgency()).isEqualTo(Urgency.HIGH);
        assertThat(normalized.warnings())
                .extracting(TextAnalysisWarningDTO::code)
                .contains("IGNORED_EXACT_DEADLINE", "MISSING_EXACT_DEADLINE");
    }

    @Test
    void deduplicatesInformationalItems() {
        TextAnalysisResponseDTO response = new TextAnalysisResponseDTO(
                "Junior Club",
                "mixed",
                List.of(),
                List.of(),
                List.of(
                        new TextAnalysisInformationalItemDTO(
                                "Junior Club kostar 3 500 kr per termin",
                                "Avgiften återbetalas inte.",
                                ItemCategory.SCHOOL,
                                "Kostnaden är därefter 3 500 kr per termin."),
                        new TextAnalysisInformationalItemDTO(
                                "Junior Club costs SEK 3,500 per term",
                                "The fee is non-refundable.",
                                ItemCategory.SCHOOL,
                                "The cost is SEK 3,500 per term.")),
                List.of());

        TextAnalysisResponseDTO normalized = postProcessor.normalize(
                response,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                ZoneId.of("Europe/Stockholm"));

        assertThat(normalized.informationalItems()).hasSize(1);
    }

    private static final String GYM_BOOKING = """
            Detaljer kring bokning:
            Namn på passet: Sparring all levels: thaiboxing/boxing/striking
            Tidpunkt för passet: 2026-11-21 12:00
            Längd på passet: 90 minuter
            """;

    @Test
    void collapsesRepeatedEventAndDropsTodosAndInformationThatOnlyRepeatIt() {
        String title = "Sparring All levels: thaiboxing/boxing/striking";
        TextAnalysisResponseDTO response = new TextAnalysisResponseDTO(
                "Bokning",
                "sv",
                List.of(
                        gymEvent(title, "Sparringpass", null, "Allstars Training Center AB", "Tidpunkt för passet"),
                        gymEvent(title, "Fullkontakt", null, null, "Sparring All levels är ett renodlat sparringpass"),
                        gymEvent(title, null, null, "Allstars", "Namn på passet")),
                List.of(
                        gymTodo(title, "2026-11-21T12:00:00+01:00"),
                        gymTodo("Bokning", "2026-11-21T12:00:00+01:00")),
                List.of(
                        new TextAnalysisInformationalItemDTO(title, null, ItemCategory.OTHER, "Namn på passet"),
                        new TextAnalysisInformationalItemDTO("Sparring All levels", null, ItemCategory.OTHER, "Namn på passet")),
                List.of());

        TextAnalysisResponseDTO normalized = postProcessor.normalize(
                response,
                OffsetDateTime.parse("2026-11-11T08:00:00+01:00"),
                ZoneId.of("Europe/Stockholm"),
                GYM_BOOKING);

        assertThat(normalized.events()).hasSize(1);
        assertThat(normalized.events().getFirst().location()).isEqualTo("Allstars Training Center AB");
        assertThat(normalized.todos()).isEmpty();
        assertThat(normalized.informationalItems()).isEmpty();
    }

    @Test
    void readsEndTimeFromDurationAndDoesNotWarnAboutYearStatedInText() {
        TextAnalysisResponseDTO response = new TextAnalysisResponseDTO(
                "Bokning",
                "sv",
                List.of(gymEvent("Sparring", null, null, null, "Tidpunkt för passet")),
                List.of(),
                List.of(),
                List.of());

        TextAnalysisResponseDTO normalized = postProcessor.normalize(
                response,
                OffsetDateTime.parse("2026-11-11T08:00:00+01:00"),
                ZoneId.of("Europe/Stockholm"),
                GYM_BOOKING);

        assertThat(normalized.events().getFirst().endDateTime()).isEqualTo("2026-11-21T13:30:00+01:00");
        assertThat(normalized.warnings()).extracting(TextAnalysisWarningDTO::code).doesNotContain("YEAR_INFERRED");
    }

    @Test
    void durationInTextReplacesEndTimeGuessedByModel() {
        TextAnalysisResponseDTO response = new TextAnalysisResponseDTO(
                "Bokning",
                "sv",
                List.of(gymEvent("Sparring", null, "2026-11-21T13:00:00+01:00", null, "Tidpunkt för passet")),
                List.of(),
                List.of(),
                List.of());

        TextAnalysisResponseDTO normalized = postProcessor.normalize(
                response,
                OffsetDateTime.parse("2026-11-11T08:00:00+01:00"),
                ZoneId.of("Europe/Stockholm"),
                GYM_BOOKING);

        assertThat(normalized.events().getFirst().endDateTime()).isEqualTo("2026-11-21T13:30:00+01:00");
    }

    @Test
    void leavesEndEmptyWhenTextHasSeveralDurations() {
        TextAnalysisResponseDTO response = new TextAnalysisResponseDTO(
                "Bokning",
                "sv",
                List.of(gymEvent("Sparring", null, null, null, "Tidpunkt för passet")),
                List.of(),
                List.of(),
                List.of());

        TextAnalysisResponseDTO normalized = postProcessor.normalize(
                response,
                OffsetDateTime.parse("2026-11-11T08:00:00+01:00"),
                ZoneId.of("Europe/Stockholm"),
                GYM_BOOKING + "Uppvärmning 15 minuter.");

        assertThat(normalized.events().getFirst().endDateTime()).isNull();
    }

    private TextAnalysisEventDTO gymEvent(String title, String description, String end, String location, String source) {
        return new TextAnalysisEventDTO(title, description, "2026-11-21T12:00:00+01:00", end, false, location,
                ItemCategory.HEALTH, Urgency.LOW, 0.9, true, source);
    }

    private TextAnalysisTodoDTO gymTodo(String title, String deadline) {
        return new TextAnalysisTodoDTO(title, null, deadline, DeadlineType.EXACT_DATE_TIME, null,
                ItemCategory.HEALTH, Urgency.LOW, 0.9, true, "Tidpunkt för passet");
    }

}
