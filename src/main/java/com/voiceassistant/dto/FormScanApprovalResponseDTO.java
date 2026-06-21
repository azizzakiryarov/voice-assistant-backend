package com.voiceassistant.dto;

import com.voiceassistant.model.FormScanStatus;

import java.util.List;

public record FormScanApprovalResponseDTO(
        Long scanId,
        FormScanStatus status,
        TextAnalysisApprovalResponseDTO approval,
        List<String> warnings
) {
}
