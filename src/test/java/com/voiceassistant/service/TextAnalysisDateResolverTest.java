package com.voiceassistant.service;

import org.junit.jupiter.api.Test;

import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TextAnalysisDateResolverTest {

    private final TextAnalysisDateResolver resolver = new TextAnalysisDateResolver();

    @Test
    void resolveMonthDayUsesReceivedYearWhenDateIsUpcoming() {
        assertThat(resolver.resolveMonthDay(
                MonthDay.of(8, 17),
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                ZoneId.of("Europe/Stockholm")))
                .hasToString("2026-08-17");
    }

    @Test
    void resolveMonthDayUsesNextYearWhenDateAlreadyPassed() {
        assertThat(resolver.resolveMonthDay(
                MonthDay.of(5, 10),
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                ZoneId.of("Europe/Stockholm")))
                .hasToString("2027-05-10");
    }

    @Test
    void sourceContainsExplicitYearDetectsFourDigitYears() {
        assertThat(resolver.sourceContainsExplicitYear("Första skoldagen är 17 augusti 2026")).isTrue();
        assertThat(resolver.sourceContainsExplicitYear("Första skoldagen är 17 augusti")).isFalse();
    }
}
