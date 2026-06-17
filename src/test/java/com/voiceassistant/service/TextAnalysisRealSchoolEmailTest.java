package com.voiceassistant.service;

import com.voiceassistant.dto.DeadlineType;
import com.voiceassistant.dto.ItemCategory;
import com.voiceassistant.dto.RecurrenceFrequency;
import com.voiceassistant.dto.TextAnalysisEventDTO;
import com.voiceassistant.dto.TextAnalysisInformationalItemDTO;
import com.voiceassistant.dto.TextAnalysisRecurrenceDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.dto.TextAnalysisTodoDTO;
import com.voiceassistant.dto.TextAnalysisWarningDTO;
import com.voiceassistant.dto.Urgency;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class TextAnalysisRealSchoolEmailTest {

    private final TextAnalysisPostProcessor postProcessor = new TextAnalysisPostProcessor(
            new TextAnalysisDateResolver(),
            new TextAnalysisUrgencyResolver());

    @Test
    void realIesEnskedeEmailProducesExpectedUniqueSuggestions() throws IOException {
        String email = loadEmailFixture();

        TextAnalysisResponseDTO normalized = postProcessor.normalize(
                llmResponseWithSwedishAndEnglishDuplicates(email),
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                ZoneId.of("Europe/Stockholm"));

        assertThat(normalized.events())
                .extracting(TextAnalysisEventDTO::title)
                .containsExactly(
                        "Första skoldagen på IES Enskede",
                        "Ordinarie skolschema börjar",
                        "Gratis provperiod för Junior Club");
        assertThat(normalized.events()).hasSize(3);
        assertThat(normalized.events().getFirst().startDateTime()).isEqualTo("2026-08-17T08:30:00+02:00");
        assertThat(normalized.events().getFirst().endDateTime()).isEqualTo("2026-08-17T10:30:00+02:00");
        assertThat(normalized.events().getFirst().location()).isEqualTo("Junior Schools lekplats, IES Enskede");

        assertThat(normalized.todos())
                .extracting(TextAnalysisTodoDTO::title)
                .containsExactly(
                        "Fyll i formuläret för specialkost",
                        "Skicka in blankett för modersmålsundervisning",
                        "Kontrollera SchoolSoft regelbundet");
        assertThat(normalized.todos()).hasSize(3);
        assertThat(normalized.todos().get(0).deadline()).isNull();
        assertThat(normalized.todos().get(0).deadlineType()).isEqualTo(DeadlineType.AS_SOON_AS_POSSIBLE);
        assertThat(normalized.todos().get(0).urgency()).isEqualTo(Urgency.HIGH);
        assertThat(normalized.todos().get(1).deadline()).isNull();
        assertThat(normalized.todos().get(2).deadlineType()).isEqualTo(DeadlineType.RECURRING);
        assertThat(normalized.todos().get(2).recurrence().frequency()).isEqualTo(RecurrenceFrequency.WEEKLY);

        assertThat(normalized.informationalItems())
                .extracting(TextAnalysisInformationalItemDTO::title)
                .containsExactly("Junior Club kostar 3 500 kr per termin");
        assertThat(normalized.informationalItems()).hasSize(1);

        assertThat(normalized.warnings())
                .extracting(TextAnalysisWarningDTO::code)
                .contains("YEAR_INFERRED", "MISSING_EXACT_DEADLINE");
    }

    private TextAnalysisResponseDTO llmResponseWithSwedishAndEnglishDuplicates(String email) {
        return new TextAnalysisResponseDTO(
                "Information om skolstarten för årskurs 4 på IES Enskede.",
                "mixed",
                List.of(
                        event(
                                "Första skoldagen på IES Enskede",
                                "Samling på Junior Schools lekplats för att träffa de nya klasserna och mentorerna.",
                                "2026-08-17T08:30:00+02:00",
                                "2026-08-17T10:30:00+02:00",
                                false,
                                "Junior Schools lekplats, IES Enskede",
                                "Första skoldagen är den 17 augusti kl. 08:30 – 10:30",
                                email),
                        event(
                                "First day of school at IES Enskede",
                                "Meet at the Junior School playground to meet the new classes and mentors.",
                                "2026-08-17T08:30:00+02:00",
                                "2026-08-17T10:30:00+02:00",
                                false,
                                "Junior Schools lekplats, IES Enskede",
                                "First Day of School: August 17th, 08:30 – 10:30",
                                email),
                        event(
                                "Ordinarie skolschema börjar",
                                "Det ordinarie schemat för årskurs 4 börjar.",
                                "2026-08-18",
                                null,
                                true,
                                null,
                                "Det ordinarie schemat börjar den 18 augusti.",
                                email),
                        event(
                                "Regular timetable begins",
                                "The regular timetable begins.",
                                "2026-08-18",
                                null,
                                true,
                                null,
                                "The regular timetable begins on August 18th.",
                                email),
                        event(
                                "Gratis provperiod för Junior Club",
                                "Årskurs 4 kan prova Junior Club kostnadsfritt.",
                                "2026-08-18",
                                "2026-08-21",
                                true,
                                null,
                                "Alla elever i årskurs 4 är välkomna att prova Junior Club kostnadsfritt mellan den 18 och 21 augusti.",
                                email),
                        event(
                                "Junior Club free trial",
                                "All Year 4 students can try Junior Club free of charge.",
                                "2026-08-18",
                                "2026-08-21",
                                true,
                                null,
                                "All Year 4 students are welcome to try Junior Club free of charge between August 18th and August 21st.",
                                email)),
                List.of(
                        todo(
                                "Fyll i formuläret för specialkost",
                                "Fyll i formuläret om barnet behöver specialkost eller har allergier.",
                                DeadlineType.AS_SOON_AS_POSSIBLE,
                                null,
                                null,
                                Urgency.LOW,
                                "ber vi er att fylla i formuläret för specialkost så snart som möjligt",
                                email),
                        todo(
                                "Complete the special dietary form",
                                "Complete the form if the child has special dietary requirements or allergies.",
                                DeadlineType.AS_SOON_AS_POSSIBLE,
                                null,
                                null,
                                Urgency.LOW,
                                "complete the special dietary form as soon as possible",
                                email),
                        todo(
                                "Skicka in blankett för modersmålsundervisning",
                                "Fyll i och skicka tillbaka den bifogade blanketten om barnet ska läsa modersmål.",
                                DeadlineType.AS_SOON_AS_POSSIBLE,
                                null,
                                null,
                                Urgency.MEDIUM,
                                "fyll i bifogat dokument och skicka tillbaka till oss så snart som möjligt",
                                email),
                        todo(
                                "Return the home language form",
                                "Fill out the attached form and return it.",
                                DeadlineType.EARLIEST_CONVENIENCE,
                                null,
                                null,
                                Urgency.MEDIUM,
                                "return it to us at your earliest convenience",
                                email),
                        todo(
                                "Kontrollera SchoolSoft regelbundet",
                                "Logga in i SchoolSoft för information från lärare och skoladministrationen.",
                                DeadlineType.RECURRING,
                                null,
                                new TextAnalysisRecurrenceDTO(RecurrenceFrequency.WEEKLY, 1),
                                Urgency.LOW,
                                "vänligen logga in regelbundet",
                                email),
                        todo(
                                "Check SchoolSoft regularly",
                                "Log in to SchoolSoft for updates from teachers and administration.",
                                DeadlineType.RECURRING,
                                null,
                                new TextAnalysisRecurrenceDTO(RecurrenceFrequency.WEEKLY, 1),
                                Urgency.LOW,
                                "please log in regularly",
                                email)),
                List.of(
                        new TextAnalysisInformationalItemDTO(
                                "Junior Club kostar 3 500 kr per termin",
                                "Avgiften efter provperioden är 3 500 SEK per termin och återbetalas inte.",
                                ItemCategory.SCHOOL,
                                source("Kostnaden är därefter 3 500 kr per termin. Observera att avgiften inte är återbetalningsbar.", email)),
                        new TextAnalysisInformationalItemDTO(
                                "Junior Club costs SEK 3,500 per semester",
                                "The fee after the trial is SEK 3,500 per semester and is non-refundable.",
                                ItemCategory.SCHOOL,
                                source("Following the trial, the cost is 3,500 SEK per semester. Please note that this fee is non-refundable.", email))),
                List.of(
                        new TextAnalysisWarningDTO(
                                "YEAR_INFERRED",
                                "Årtalet anges inte uttryckligen i mejlet. År 2026 har antagits baserat på mottagningsdatumet.",
                                true),
                        new TextAnalysisWarningDTO(
                                "MISSING_EXACT_DEADLINE",
                                "Specialkost och modersmålsblankett saknar ett exakt sista datum.",
                                true))
        );
    }

    private TextAnalysisEventDTO event(
            String title,
            String description,
            String start,
            String end,
            boolean allDay,
            String location,
            String sourceText,
            String email) {
        return new TextAnalysisEventDTO(
                title,
                description,
                start,
                end,
                allDay,
                location,
                ItemCategory.SCHOOL,
                Urgency.MEDIUM,
                0.95,
                true,
                source(sourceText, email));
    }

    private TextAnalysisTodoDTO todo(
            String title,
            String description,
            DeadlineType deadlineType,
            String deadline,
            TextAnalysisRecurrenceDTO recurrence,
            Urgency urgency,
            String sourceText,
            String email) {
        return new TextAnalysisTodoDTO(
                title,
                description,
                deadline,
                deadlineType,
                recurrence,
                ItemCategory.SCHOOL,
                urgency,
                0.95,
                true,
                source(sourceText, email));
    }

    private String source(String expectedSourceText, String email) {
        assertThat(email).contains(expectedSourceText);
        return expectedSourceText;
    }

    private String loadEmailFixture() throws IOException {
        try (var inputStream = getClass().getResourceAsStream("/text-analysis/ies-enskede-year4-email.txt")) {
            return new String(Objects.requireNonNull(inputStream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
