package com.voiceassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record TextAnalysisRequestDTO(
        @Size(max = 160, message = "title must be at most 160 characters")
        String title,

        @NotBlank(message = "text is required")
        @Size(max = 12000, message = "text must be at most 12000 characters")
        String text,

        SourceType sourceType,
        OffsetDateTime receivedAt,

        @Size(max = 64, message = "timeZone must be at most 64 characters")
        String timeZone
) {
}
