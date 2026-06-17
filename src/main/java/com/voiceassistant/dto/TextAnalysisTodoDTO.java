package com.voiceassistant.dto;

public record TextAnalysisTodoDTO(
        String title,
        String description,
        String deadline,
        DeadlineType deadlineType,
        TextAnalysisRecurrenceDTO recurrence,
        ItemCategory category,
        Urgency urgency,
        Double confidence,
        Boolean requiresConfirmation,
        String sourceText
) {
}
