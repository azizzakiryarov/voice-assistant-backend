package com.voiceassistant.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Entity
@Data
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private LocalDate startTimestamp;
    private LocalDate endTimestamp;
    @OneToMany(cascade = CascadeType.ALL)
    private List<Participants> participants;
}