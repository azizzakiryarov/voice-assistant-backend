package com.voiceassistant.controller;

import com.voiceassistant.dto.TextAnalysisApprovalRequestDTO;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.exception.TextAnalysisException;
import com.voiceassistant.service.TextAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/text-analysis")
@RequiredArgsConstructor
public class TextAnalysisController {

    private final TextAnalysisService textAnalysisService;

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

    @PostMapping("/approve")
    public ResponseEntity<?> approve(@RequestBody TextAnalysisApprovalRequestDTO request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(textAnalysisService.approve(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
