package com.voiceassistant.controller;

import com.voiceassistant.dto.FormScanApprovalRequestDTO;
import com.voiceassistant.exception.FormScanException;
import com.voiceassistant.exception.FormScanOcrException;
import com.voiceassistant.service.FormScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/form-scans")
@RequiredArgsConstructor
public class FormScanController {

    private final FormScanService formScanService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> scan(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(formScanService.scan(file));
        } catch (FormScanOcrException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        } catch (FormScanException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{scanId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long scanId, @RequestBody FormScanApprovalRequestDTO request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(formScanService.approve(scanId, request));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (FormScanException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
