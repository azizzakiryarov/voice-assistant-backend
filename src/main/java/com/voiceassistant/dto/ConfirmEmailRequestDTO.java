package com.voiceassistant.dto;

import lombok.Data;

@Data
public class ConfirmEmailRequestDTO {
    private String email;
    private String transcription;
}