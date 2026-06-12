package com.voiceassistant.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private LocalDateTime startTimestamp;
    private LocalDateTime endTimestamp;
    @OneToMany(cascade = CascadeType.ALL)
    private List<Participants> participants;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser owner;
}
