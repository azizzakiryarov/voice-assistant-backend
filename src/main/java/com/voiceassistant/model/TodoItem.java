package com.voiceassistant.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Data
public class TodoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String description;
    private LocalDate dueDate;
    private boolean completed;
    private String googleTaskId;
    private String googleTaskListId;
    private String googlePosition;
    private OffsetDateTime googleUpdatedAt;
    private String syncStatus;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser owner;
}
