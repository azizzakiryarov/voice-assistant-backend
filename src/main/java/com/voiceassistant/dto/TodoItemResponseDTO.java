package com.voiceassistant.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TodoItemResponseDTO {
    private Long id;
    private String description;
    private LocalDate dueDate;
    private boolean completed;
}