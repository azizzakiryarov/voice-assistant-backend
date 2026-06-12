package com.voiceassistant.dto;

import lombok.Data;

@Data
public class VoiceCommandApprovalResponseDTO {
    private VoiceCommandType type;
    private Object saved;
    private boolean googleSynced;
    private String googleService;
}
