package com.voiceassistant.controller;

import com.voiceassistant.dto.ConfirmEmailRequestDTO;
import com.voiceassistant.dto.TodoItemRequestDTO;
import com.voiceassistant.dto.TranscriptionResponseDTO;
import com.voiceassistant.dto.VoiceCommandApprovalRequestDTO;
import com.voiceassistant.dto.VoiceCommandPreviewDTO;
import com.voiceassistant.service.CommandProcessorService;
import com.voiceassistant.service.TranscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;

import java.util.Map;

@RestController
@RequestMapping("/api/voice-assistent")
@RequiredArgsConstructor
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    private final CommandProcessorService  commandProcessorService;

    @PostMapping("/text")
    public ResponseEntity<?> processTextCommand(@Valid @RequestBody TodoItemRequestDTO todoItemRequestDTO) {
        try {
            return commandProcessorService.processCommand(todoItemRequestDTO.getDescription(), todoItemRequestDTO.getDueDate(), null);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("message", "An error occurred: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> transcribeAudio(@RequestParam("file") MultipartFile audioFile) {
        try {
            TranscriptionResponseDTO response = transcriptionService.transcribeAudio(audioFile);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/voice-command/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> previewVoiceCommand(@RequestParam("file") MultipartFile audioFile) {
        try {
            TranscriptionResponseDTO transcription = transcriptionService.transcribeAudio(audioFile);
            VoiceCommandPreviewDTO preview = commandProcessorService.previewCommand(transcription.getTranscription());
            preview.setExtractedEmail(transcription.getExtractedEmail());
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/voice-command/approve")
    public ResponseEntity<?> approveVoiceCommand(@RequestBody VoiceCommandApprovalRequestDTO request) {
        try {
            return commandProcessorService.approveCommand(request);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/confirm-email")
    public ResponseEntity<?> confirmEmail(@RequestBody ConfirmEmailRequestDTO request) {
        try {
            return commandProcessorService.processConfirmedEmail(request.getTranscription(), request.getEmail());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
