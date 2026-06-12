package com.voiceassistant.controller;

import com.voiceassistant.dto.SyncStatusDTO;
import com.voiceassistant.dto.UserProfileDTO;
import com.voiceassistant.service.AppUserService;
import com.voiceassistant.integration.google.service.GoogleCalendarService;
import com.voiceassistant.integration.google.service.GoogleTasksService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/voice-assistent/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserService appUserService;
    private final GoogleCalendarService googleCalendarService;
    private final GoogleTasksService googleTasksService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> me() {
        return ResponseEntity.ok(appUserService.currentProfile());
    }

    @GetMapping("/sync-status")
    public ResponseEntity<SyncStatusDTO> syncStatus() {
        SyncStatusDTO status = new SyncStatusDTO();
        status.setGoogleCalendar(googleCalendarService.hasCurrentUserCalendarToken() ? "CONNECTED" : "DISCONNECTED");
        status.setGoogleTasks(googleTasksService.hasCurrentUserTasksToken() ? "CONNECTED" : "DISCONNECTED");
        return ResponseEntity.ok(status);
    }
}
