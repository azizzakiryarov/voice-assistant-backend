package com.voiceassistant.service;

import com.voiceassistant.dto.TextAnalysisJobStatus;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.model.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TextAnalysisJobServiceTest {

    private TextAnalysisService textAnalysisService;
    private AppUserService appUserService;
    private TextAnalysisJobService jobService;

    @BeforeEach
    void setUp() {
        textAnalysisService = mock(TextAnalysisService.class);
        appUserService = mock(AppUserService.class);
        jobService = new TextAnalysisJobService(textAnalysisService, appUserService);
    }

    @AfterEach
    void tearDown() {
        jobService.shutdown();
    }

    @Test
    void startJobRunsAnalysisAsynchronouslyAndExposesResult() throws Exception {
        AppUser owner = user(1L);
        CountDownLatch analyzed = new CountDownLatch(1);
        when(appUserService.getCurrentUser()).thenReturn(owner);
        when(textAnalysisService.analyze(any())).thenAnswer(invocation -> {
            analyzed.countDown();
            return new TextAnalysisResponseDTO("Klar", "sv", List.of(), List.of(), List.of(), List.of());
        });

        var started = jobService.startJob(request());

        assertThat(started.jobId()).isNotBlank();
        assertThat(started.status()).isIn(
                TextAnalysisJobStatus.PENDING,
                TextAnalysisJobStatus.RUNNING,
                TextAnalysisJobStatus.SUCCEEDED);
        assertThat(analyzed.await(2, TimeUnit.SECONDS)).isTrue();

        var completed = jobService.getJob(started.jobId());

        assertThat(completed.status()).isEqualTo(TextAnalysisJobStatus.SUCCEEDED);
        assertThat(completed.result().summary()).isEqualTo("Klar");
    }

    @Test
    void getJobRejectsOtherUsers() {
        AppUser owner = user(1L);
        AppUser otherUser = user(2L);
        when(appUserService.getCurrentUser()).thenReturn(owner, otherUser);
        when(textAnalysisService.analyze(any())).thenReturn(
                new TextAnalysisResponseDTO("Klar", "sv", List.of(), List.of(), List.of(), List.of()));

        var started = jobService.startJob(request());

        assertThatThrownBy(() -> jobService.getJob(started.jobId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("another user");
    }

    private TextAnalysisRequestDTO request() {
        return new TextAnalysisRequestDTO(
                "Mejl från skolan",
                "Första skoldagen är den 17 augusti.",
                null,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                "Europe/Stockholm");
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setGoogleSubject("subject-" + id);
        user.setEmail("user" + id + "@example.com");
        return user;
    }
}
