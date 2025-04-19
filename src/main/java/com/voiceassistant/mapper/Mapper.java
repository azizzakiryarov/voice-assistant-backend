package com.voiceassistant.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.voiceassistant.exception.AudioTranslationException;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.Participants;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Mapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Event mapMeetingToEvent(Meeting meeting, String email) {
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
        new EventAttendee().setEmail(email);
        attendees.add(new EventAttendee().setEmail(email));
        newEvent.setAttendees(attendees);
        return newEvent;
    }

    public static DateTime convertLocalDateTimeToGoogleDateTime(LocalDateTime localDateTime) {
        Date date = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        return new DateTime(date);
    }

    public static String extractTranslatedText(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return jsonNode.path("text").asText();
        } catch (JsonProcessingException e) {
            throw new AudioTranslationException("Failed to parse translation response", e);
        }
    }
}