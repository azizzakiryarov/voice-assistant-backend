package com.voiceassistant.dto;

public record TextAnalysisWarningDTO(
        String code,
        String message,
        Boolean requiresUserAttention
) {
}
