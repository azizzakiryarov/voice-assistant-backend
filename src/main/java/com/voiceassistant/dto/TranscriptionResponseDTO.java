package com.voiceassistant.dto;

import lombok.Data;

@Data
public class TranscriptionResponseDTO {
    private String transcription;
    private String extractedEmail;
}