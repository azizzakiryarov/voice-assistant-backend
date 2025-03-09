package com.voiceassistant.dto;

import com.voiceassistant.model.Participants;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MeetingRequestDTO {
    @NotBlank(message = "title is required")
    private String title;
    @NotBlank(message = "startTimestamp is required")
    private LocalDateTime startTimestamp;
    @NotBlank(message = "endTimestamp is required")
    private LocalDateTime endTimestamp;
    private List<Participants> participants;
}