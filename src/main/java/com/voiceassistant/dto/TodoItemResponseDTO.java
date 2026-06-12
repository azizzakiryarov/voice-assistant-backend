package com.voiceassistant.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
public class TodoItemResponseDTO {
    private Long id;
    private String description;
    private LocalDate dueDate;
    private boolean completed;
    private String googleTaskId;
    private String googleTaskListId;
    private OffsetDateTime googleUpdatedAt;
    private String syncStatus;
}
