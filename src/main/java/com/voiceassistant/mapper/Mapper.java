package com.voiceassistant.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.voiceassistant.config.AppTimeZone;
import com.voiceassistant.dto.MeetingResponseDTO;
import com.voiceassistant.dto.TodoItemResponseDTO;
import com.voiceassistant.exception.AudioTranslationException;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.Participants;
import com.voiceassistant.model.TodoItem;

import java.time.LocalDateTime;
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
                .setTimeZone(AppTimeZone.DEFAULT.getId());

        newEvent.setStart(start);

        EventDateTime end = new EventDateTime()
                .setDateTime(convertLocalDateTimeToGoogleDateTime(meeting.getEndTimestamp()))
                .setTimeZone(AppTimeZone.DEFAULT.getId());
        newEvent.setEnd(end);

        if (email != null && !email.isBlank()) {
            List<EventAttendee> attendees = new ArrayList<>();
            attendees.add(new EventAttendee().setEmail(email));
            newEvent.setAttendees(attendees);
        }
        return newEvent;
    }

    public static MeetingResponseDTO toMeetingResponse(Meeting meeting) {
        MeetingResponseDTO response = new MeetingResponseDTO();
        response.setId(meeting.getId());
        response.setTitle(meeting.getTitle());
        response.setStartTimestamp(meeting.getStartTimestamp());
        response.setEndTimestamp(meeting.getEndTimestamp());
        response.setParticipants(meeting.getParticipants());
        return response;
    }

    public static TodoItemResponseDTO toTodoResponse(TodoItem todoItem) {
        TodoItemResponseDTO response = new TodoItemResponseDTO();
        response.setId(todoItem.getId());
        response.setDescription(todoItem.getDescription());
        response.setDueDate(todoItem.getDueDate());
        response.setCompleted(todoItem.isCompleted());
        response.setGoogleTaskId(todoItem.getGoogleTaskId());
        response.setGoogleTaskListId(todoItem.getGoogleTaskListId());
        response.setGooglePosition(todoItem.getGooglePosition());
        response.setGoogleUpdatedAt(todoItem.getGoogleUpdatedAt());
        response.setSyncStatus(todoItem.getSyncStatus());
        return response;
    }

    public static DateTime convertLocalDateTimeToGoogleDateTime(LocalDateTime localDateTime) {
        Date date = Date.from(localDateTime.atZone(AppTimeZone.DEFAULT).toInstant());
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
