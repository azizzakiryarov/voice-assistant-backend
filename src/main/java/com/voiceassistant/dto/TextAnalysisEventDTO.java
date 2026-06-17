package com.voiceassistant.dto;

public record TextAnalysisEventDTO(
        String title,
        String description,
        String startDateTime,
        String endDateTime,
        Boolean allDay,
        String location,
        ItemCategory category,
        Urgency urgency,
        Double confidence,
        Boolean requiresConfirmation,
        String sourceText
) {
}
