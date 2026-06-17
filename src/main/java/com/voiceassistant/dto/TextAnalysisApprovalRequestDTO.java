package com.voiceassistant.dto;

import java.util.List;

public record TextAnalysisApprovalRequestDTO(
        List<TextAnalysisEventDTO> events,
        List<TextAnalysisTodoDTO> todos
) {
}
