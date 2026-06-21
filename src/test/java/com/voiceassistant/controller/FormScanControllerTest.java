package com.voiceassistant.controller;

import com.voiceassistant.dto.FormScanResponseDTO;
import com.voiceassistant.service.FormScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FormScanControllerTest {

    private FormScanService formScanService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        formScanService = mock(FormScanService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FormScanController(formScanService)).build();
    }

    @Test
    void postFormScanAcceptsImageAndReturnsReviewDraft() throws Exception {
        when(formScanService.scan(any())).thenReturn(new FormScanResponseDTO(
                42L, com.voiceassistant.model.FormScanStatus.READY_FOR_REVIEW, "daycare_schedule", "Ada",
                "Dagisschema", "Barnets namn: Ada", List.of(), List.of(), 0.87, List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "schedule.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2});
        mockMvc.perform(multipart("/api/form-scans").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scanId").value(42))
                .andExpect(jsonPath("$.detectedFormType").value("daycare_schedule"))
                .andExpect(jsonPath("$.suggestedTodos").isArray());
    }
}
