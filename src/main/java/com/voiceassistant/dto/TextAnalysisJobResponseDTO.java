package com.voiceassistant.dto;

public record TextAnalysisJobResponseDTO(
        String jobId,
        TextAnalysisJobStatus status,
        TextAnalysisResponseDTO result,
        String message
) {
}
