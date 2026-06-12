package com.voiceassistant.integration.google.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.voiceassistant.integration.google.config.GoogleCalendarConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final GoogleCalendarConfig googleCalendarConfig;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public List<Event> getUpcomingEvents(int maxResults) throws IOException {
        Calendar calendarService = currentUserCalendarClient();
        DateTime now = new DateTime(System.currentTimeMillis());
        Events events = calendarService.events().list("primary")
                .setMaxResults(maxResults)
                .setTimeMin(now)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        return events.getItems();
    }

    public void createEvent(Event event) throws IOException {
        Calendar calendarService = currentUserCalendarClient();
        Event executed = calendarService.events().insert("primary", event).execute();
        log.info("Event created: {}", executed.getSummary());
        log.info("Event link: {}", executed.getHtmlLink());
    }

    public Event updateEvent(String eventId, Event event) throws IOException {
        Calendar calendarService = currentUserCalendarClient();
        return calendarService.events().update("primary", eventId, event).execute();
    }

    public void deleteEvent(String eventId) throws IOException {
        Calendar calendarService = currentUserCalendarClient();
        calendarService.events().delete("primary", eventId).execute();
    }

    public boolean hasCurrentUserCalendarToken() {
        return currentAuthorizedClient() != null;
    }

    private Calendar currentUserCalendarClient() throws IOException {
        OAuth2AuthorizedClient authorizedClient = currentAuthorizedClient();
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new IOException("Google Calendar is not connected for current user");
        }
        try {
            return googleCalendarConfig.calendarClient(authorizedClient.getAccessToken().getTokenValue());
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not create Google Calendar client", e);
        }
    }

    private OAuth2AuthorizedClient currentAuthorizedClient() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return null;
        }
        return authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName());
    }
}
