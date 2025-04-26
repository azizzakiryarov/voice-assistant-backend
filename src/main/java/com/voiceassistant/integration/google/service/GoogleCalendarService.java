package com.voiceassistant.integration.google.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import groovy.util.logging.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@lombok.extern.slf4j.Slf4j
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    @Value("${google.service-account.key-file}")
    private Resource serviceAccountKeyFile;

    @Value("${google.calendar.calendar-id}")
    private String calendarId;

    private Calendar getCalendarService() throws GeneralSecurityException, IOException {
        GoogleCredential credential = GoogleCredential.fromStream(serviceAccountKeyFile.getInputStream())
                .createScoped(Collections.singleton(CalendarScopes.CALENDAR));

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Spring Boot Calendar App")
                .build();
    }

    public List<Event> getUpcomingEvents(int maxResults) throws IOException, GeneralSecurityException {
        DateTime now = new DateTime(System.currentTimeMillis());
        Events events = getCalendarService().events().list("primary")
                .setMaxResults(maxResults)
                .setTimeMin(now)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        return events.getItems();
    }

    public Event createEvent(Event event) throws IOException, GeneralSecurityException {
        Event executed = getCalendarService().events().insert("primary", event).execute();
        log.info("Event created: {}", executed.getSummary());
        log.info("Event link: {}", executed.getHtmlLink());
        return executed;
    }

    public Event updateEvent(String eventId, Event event) throws IOException, GeneralSecurityException {
        return getCalendarService().events().update("primary", eventId, event).execute();
    }

    public void deleteEvent(String eventId) throws IOException, GeneralSecurityException {
        getCalendarService().events().delete("primary", eventId).execute();
    }
}