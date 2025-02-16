package com.voiceassistant.integration.google.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final Calendar calendarService;

    public List<Event> getUpcomingEvents(int maxResults) throws IOException {
        DateTime now = new DateTime(System.currentTimeMillis());
        Events events = calendarService.events().list("primary")
                .setMaxResults(maxResults)
                .setTimeMin(now)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        return events.getItems();
    }

    public Event createEvent(Event event) throws IOException {
        Event executed = calendarService.events().insert("primary", event).execute();
        System.out.println(executed.getSummary());
        System.out.println(executed.getHtmlLink());
        return executed;
    }

    public Event updateEvent(String eventId, Event event) throws IOException {
        return calendarService.events().update("primary", eventId, event).execute();
    }

    public void deleteEvent(String eventId) throws IOException {
        calendarService.events().delete("primary", eventId).execute();
    }
}