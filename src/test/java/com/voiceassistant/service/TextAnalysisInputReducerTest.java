package com.voiceassistant.service;

import com.voiceassistant.dto.SourceType;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class TextAnalysisInputReducerTest {

    private final TextAnalysisInputReducer reducer = new TextAnalysisInputReducer();

    @Test
    void reducesRealBilingualSchoolEmailWithoutDroppingKeyFacts() throws IOException {
        String email = loadEmailFixture();

        TextAnalysisInputReducer.Result result = reducer.reduce(request(email));
        String reduced = result.request().text();

        assertThat(result.reduced()).isTrue();
        assertThat(result.appliedRules())
                .contains("signature_removed", "bilingual_duplicate_removed", "irrelevant_paragraphs_removed");
        assertThat(result.reducedLength()).isLessThan(result.originalLength() / 2);

        assertThat(reduced)
                .contains("Första skoldagen är den 17 augusti")
                .contains("Specialkost")
                .contains("Junior Club")
                .contains("3 500 kr")
                .contains("SchoolSoft")
                .contains("Modersmål");
        assertThat(reduced)
                .doesNotContain("Dear Parents and Guardians")
                .doesNotContain("Yours Sincerely")
                .doesNotContain("Lingvägen 123");
    }

    @Test
    void keepsShortTextUnchanged() {
        String text = "Första skoldagen är den 17 augusti kl. 08:30 – 10:30.";

        TextAnalysisInputReducer.Result result = reducer.reduce(request(text));

        assertThat(result.reduced()).isFalse();
        assertThat(result.request().text()).isEqualTo(text);
        assertThat(result.appliedRules()).isEmpty();
    }

    @Test
    void doesNotDropLongInformationalTextWithoutRelevantExtractionSignal() {
        String text = "Det här är ett allmänt informationsbrev utan datum eller uppgifter.\n\n".repeat(40);

        TextAnalysisInputReducer.Result result = reducer.reduce(request(text));

        assertThat(result.request().text()).isEqualTo(text.trim());
        assertThat(result.appliedRules()).isEmpty();
    }

    private TextAnalysisRequestDTO request(String text) {
        return new TextAnalysisRequestDTO(
                "Mejl från skolan",
                text,
                SourceType.EMAIL,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                "Europe/Stockholm");
    }

    private String loadEmailFixture() throws IOException {
        try (var inputStream = getClass().getResourceAsStream("/text-analysis/ies-enskede-year4-email.txt")) {
            return new String(Objects.requireNonNull(inputStream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
