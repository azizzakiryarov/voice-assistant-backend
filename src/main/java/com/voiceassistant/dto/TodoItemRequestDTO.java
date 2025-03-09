package com.voiceassistant.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

@Data
public class TodoItemRequestDTO {
    @NotBlank(message = "description is required")
    private String description;
    private LocalDate dueDate;
    private boolean completed;
}