package com.voiceassistant.dto;

import lombok.Data;

@Data
public class VoiceCommandApprovalRequestDTO {
    private String transcription;
    private VoiceCommandType type;
    private TodoItemRequestDTO todo;
    private MeetingRequestDTO meeting;
}
