package com.voiceassistant.mapper;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.Participants;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class EventMapper {

    public static Event mapMeetingToEvent(Meeting meeting){
        Event newEvent = new Event();
        newEvent.setSummary(meeting.getTitle());

        EventDateTime start = new EventDateTime()
                .setDateTime(convertLocalDateTimeToGoogleDateTime(meeting.getStartTimestamp()))
                .setTimeZone("Europe/Stockholm");

        newEvent.setStart(start);

        EventDateTime end = new EventDateTime()
                .setDateTime(convertLocalDateTimeToGoogleDateTime(meeting.getEndTimestamp()))
                .setTimeZone("Europe/Stockholm");
        newEvent.setEnd(end);

        // Add attendees
        List<EventAttendee> attendees = new ArrayList<>();
        for (Participants participant : meeting.getParticipants()) {
            var participantEmail = participant.getEmail();
            new EventAttendee().setEmail(participantEmail);
            attendees.add(new EventAttendee().setEmail(participantEmail));
        }
        newEvent.setAttendees(attendees);
        return newEvent;
    }

    public static DateTime convertLocalDateTimeToGoogleDateTime(LocalDateTime localDateTime) {
        Date date = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        return new DateTime(date);
    }
}