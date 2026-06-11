package com.voiceassistant.controller;

import com.voiceassistant.dto.ConfirmEmailRequestDTO;
import com.voiceassistant.dto.TodoItemRequestDTO;
import com.voiceassistant.dto.TranscriptionResponseDTO;
import com.voiceassistant.service.CommandProcessorService;
import com.voiceassistant.service.TranscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

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

    @PostMapping("/transcribe")
    public ResponseEntity<TranscriptionResponseDTO> transcribeAudio(@RequestParam("file") MultipartFile audioFile) {
        try {
            TranscriptionResponseDTO response = transcriptionService.transcribeAudio(audioFile);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
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
