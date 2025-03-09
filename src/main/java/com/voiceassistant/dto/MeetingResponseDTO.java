package com.voiceassistant.dto;

import com.voiceassistant.model.Participants;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MeetingResponseDTO {
    private Long id;
    private String title;
    private LocalDateTime startTimestamp;
    private LocalDateTime endTimestamp;
    private List<Participants> participants;
}