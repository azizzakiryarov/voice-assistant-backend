package com.voiceassistant.controller;

import com.voiceassistant.dto.ConfirmEmailRequestDTO;
import com.voiceassistant.dto.TranscriptionResponseDTO;
import com.voiceassistant.model.VoiceCommand;
import com.voiceassistant.service.CommandProcessorService;
import com.voiceassistant.service.TranscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice-assistent")
@RequiredArgsConstructor
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    private final CommandProcessorService  commandProcessorService;

    @PostMapping("/text")
    public ResponseEntity<String> processVoiceCommand(@RequestBody VoiceCommand command) {
        try {
            ResponseEntity<String> processedCommand = commandProcessorService.processCommand(command.getText(), null);
            return ResponseEntity.ok("Your text message has been processed" + processedCommand);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred: " + e);
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
            commandProcessorService.processConfirmedEmail(request.getEmail(), request.getTranscription());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}