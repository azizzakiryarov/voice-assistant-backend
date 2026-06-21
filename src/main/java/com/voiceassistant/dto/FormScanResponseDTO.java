package com.voiceassistant.dto;

import com.voiceassistant.model.FormScanStatus;

import java.util.List;

public record FormScanResponseDTO(
        Long scanId,
        FormScanStatus status,
        String detectedFormType,
        String childName,
        String summary,
        String extractedText,
        List<TextAnalysisTodoDTO> suggestedTodos,
        List<TextAnalysisEventDTO> suggestedCalendarEvents,
        Double confidence,
        List<TextAnalysisWarningDTO> warnings
) {
}
