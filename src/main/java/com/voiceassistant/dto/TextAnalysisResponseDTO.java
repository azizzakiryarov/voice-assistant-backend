package com.voiceassistant.dto;

import java.util.List;

public record TextAnalysisResponseDTO(
        String summary,
        String language,
        List<TextAnalysisEventDTO> events,
        List<TextAnalysisTodoDTO> todos,
        List<TextAnalysisInformationalItemDTO> informationalItems,
        List<TextAnalysisWarningDTO> warnings
) {
}
