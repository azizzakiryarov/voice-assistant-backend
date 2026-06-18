package com.voiceassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.voiceassistant.dto.TextAnalysisJobResponseDTO;
import com.voiceassistant.dto.TextAnalysisJobStatus;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.exception.TextAnalysisException;
import com.voiceassistant.service.TextAnalysisJobService;
import com.voiceassistant.service.TextAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TextAnalysisControllerTest {

    private MockMvc mockMvc;
    private TextAnalysisService textAnalysisService;
    private TextAnalysisJobService textAnalysisJobService;
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setUp() {
        textAnalysisService = mock(TextAnalysisService.class);
        textAnalysisJobService = mock(TextAnalysisJobService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TextAnalysisController(textAnalysisService, textAnalysisJobService)).build();
    }

    @Test
    void postTextAnalysisReturnsStructuredAnalysis() throws Exception {
        when(textAnalysisService.analyze(any())).thenReturn(new TextAnalysisResponseDTO(
                "Information om skolstarten.",
                "sv",
                List.of(),
                List.of(),
                List.of(),
                List.of()));

        TextAnalysisRequestDTO request = new TextAnalysisRequestDTO(
                "Mejl från skolan",
                "Första skoldagen är den 17 augusti.",
                null,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                "Europe/Stockholm");

        mockMvc.perform(post("/api/text-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Information om skolstarten."))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.todos").isArray());
    }

    @Test
    void postTextAnalysisReturnsBadGatewayWhenLlmJsonIsInvalid() throws Exception {
        when(textAnalysisService.analyze(any())).thenThrow(new TextAnalysisException("LLM returned invalid text analysis JSON"));

        TextAnalysisRequestDTO request = new TextAnalysisRequestDTO(
                "Mejl från skolan",
                "Text",
                null,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                "Europe/Stockholm");

        mockMvc.perform(post("/api/text-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("LLM returned invalid text analysis JSON"));
    }

    @Test
    void postTextAnalysisJobReturnsAcceptedJob() throws Exception {
        when(textAnalysisJobService.startJob(any())).thenReturn(new TextAnalysisJobResponseDTO(
                "job-1",
                TextAnalysisJobStatus.PENDING,
                null,
                null));

        TextAnalysisRequestDTO request = new TextAnalysisRequestDTO(
                "Mejl från skolan",
                "Första skoldagen är den 17 augusti.",
                null,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                "Europe/Stockholm");

        mockMvc.perform(post("/api/text-analysis/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getTextAnalysisJobReturnsJobStatus() throws Exception {
        when(textAnalysisJobService.getJob("job-1")).thenReturn(new TextAnalysisJobResponseDTO(
                "job-1",
                TextAnalysisJobStatus.SUCCEEDED,
                new TextAnalysisResponseDTO("Klar", "sv", List.of(), List.of(), List.of(), List.of()),
                null));

        mockMvc.perform(get("/api/text-analysis/jobs/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result.summary").value("Klar"));
    }
}
