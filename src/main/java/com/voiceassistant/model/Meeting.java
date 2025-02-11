package com.voiceassistant.model;

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
}

/*
{
  "type" : "MEETING",
  "details" : {
    "date" : "1 april",
    "start_time" : "10:00",
    "end_time" : "17:00",
    "attendees" : [ "zakiryarov@hotmail.com" ]
  }
}**/