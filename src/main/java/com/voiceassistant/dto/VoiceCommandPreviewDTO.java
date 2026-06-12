package com.voiceassistant.dto;

import lombok.Data;

@Data
public class VoiceCommandPreviewDTO {
    private String transcription;
    private VoiceCommandType type = VoiceCommandType.UNKNOWN;
    private TodoItemRequestDTO todo;
    private MeetingRequestDTO meeting;
    private String extractedEmail;
    private String message;
}
