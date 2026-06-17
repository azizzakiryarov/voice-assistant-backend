package com.voiceassistant.dto;

public record TextAnalysisInformationalItemDTO(
        String title,
        String description,
        ItemCategory category,
        String sourceText
) {
}
