package com.voiceassistant.mapper;

import com.google.api.services.calendar.model.Event;
import com.voiceassistant.model.Meeting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class MapperTest {

    private TimeZone originalTimeZone;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void mapMeetingToEventUsesStockholmTimeEvenWhenServerRunsInUtc() {
        Meeting meeting = new Meeting();
        meeting.setTitle("Möte");
        meeting.setStartTimestamp(LocalDateTime.of(2026, 10, 15, 10, 0));
        meeting.setEndTimestamp(LocalDateTime.of(2026, 10, 15, 11, 0));

        Event event = Mapper.mapMeetingToEvent(meeting, null);

        long expectedStart = meeting.getStartTimestamp()
                .atZone(ZoneId.of("Europe/Stockholm"))
                .toInstant()
                .toEpochMilli();
        assertThat(event.getStart().getDateTime().getValue()).isEqualTo(expectedStart);
        assertThat(event.getStart().getTimeZone()).isEqualTo("Europe/Stockholm");
    }
}
