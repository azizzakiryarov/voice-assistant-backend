package com.voiceassistant.dto;

public record TextAnalysisRecurrenceDTO(
        RecurrenceFrequency frequency,
        Integer interval
) {
}
