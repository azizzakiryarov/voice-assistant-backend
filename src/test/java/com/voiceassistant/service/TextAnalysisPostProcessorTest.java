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
}
