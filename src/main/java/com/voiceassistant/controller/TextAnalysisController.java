package com.voiceassistant.controller;

import com.voiceassistant.dto.TextAnalysisApprovalRequestDTO;
import com.voiceassistant.dto.TextAnalysisJobResponseDTO;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.exception.TextAnalysisException;
import com.voiceassistant.service.TextAnalysisJobService;
import com.voiceassistant.service.TextAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/text-analysis")
@RequiredArgsConstructor
public class TextAnalysisController {

    private final TextAnalysisService textAnalysisService;
    private final TextAnalysisJobService textAnalysisJobService;

    @PostMapping
    public ResponseEntity<?> analyze(@Valid @RequestBody TextAnalysisRequestDTO request) {
        try {
            return ResponseEntity.ok(textAnalysisService.analyze(request));
        } catch (TextAnalysisException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/jobs")
    public ResponseEntity<?> startAnalysisJob(@Valid @RequestBody TextAnalysisRequestDTO request) {
        try {
            TextAnalysisJobResponseDTO response = textAnalysisJobService.startJob(request);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (TextAnalysisException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getAnalysisJob(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(textAnalysisJobService.getJob(jobId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/approve")
    public ResponseEntity<?> approve(@RequestBody TextAnalysisApprovalRequestDTO request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(textAnalysisService.approve(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
