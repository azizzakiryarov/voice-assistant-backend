package com.voiceassistant.integration.google.service;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.voiceassistant.integration.google.config.GoogleCalendarConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleCalendarServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createEventAsksGoogleToEmailInvitationsToGuests() throws Exception {
        List<String> requestedUrls = new ArrayList<>();
        MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                requestedUrls.add(method + " " + url);
                return new MockLowLevelHttpRequest(url) {
                    @Override
                    public MockLowLevelHttpResponse execute() {
                        return new MockLowLevelHttpResponse()
                                .setContentType("application/json")
                                .setContent("{\"id\":\"evt-1\"}");
                    }
                };
            }
        };
        Calendar calendar = new Calendar.Builder(transport, GsonFactory.getDefaultInstance(), null)
                .setApplicationName("test")
                .build();

        GoogleCalendarConfig config = mock(GoogleCalendarConfig.class);
        when(config.calendarClient(anyString())).thenReturn(calendar);
        OAuth2AuthorizedClientService clients = mock(OAuth2AuthorizedClientService.class);
        when(clients.loadAuthorizedClient("google", "user-1")).thenReturn(authorizedClient());
        signIn();

        new GoogleCalendarService(config, clients).createEvent(new Event()
                .setSummary("Möte med Anna")
                .setAttendees(List.of(new EventAttendee().setEmail("anna@example.com"))));

        assertThat(requestedUrls).singleElement().asString()
                .startsWith("POST ")
                .contains("/calendars/primary/events")
                .contains("sendUpdates=all");
    }

    private static void signIn() {
        DefaultOAuth2User user = new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("ROLE_USER"), Map.of("sub", "user-1"), "sub");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
    }

    private static OAuth2AuthorizedClient authorizedClient() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .authorizationUri("http://localhost/auth")
                .tokenUri("http://localhost/token")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(3600));
        return new OAuth2AuthorizedClient(registration, "user-1", token);
    }
}
