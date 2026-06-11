package com.voiceassistant.dto;

import com.voiceassistant.model.Participants;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MeetingRequestDTO {
    @NotBlank(message = "title is required")
    private String title;
    @NotNull(message = "startTimestamp is required")
    private LocalDateTime startTimestamp;
    @NotNull(message = "endTimestamp is required")
    private LocalDateTime endTimestamp;
    private List<Participants> participants;
}
