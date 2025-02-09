package com.voiceassistant.controller;

import com.voiceassistant.model.VoiceCommand;
import com.voiceassistant.service.CommandProcessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceCommandController {
    private final CommandProcessorService commandProcessor;

    @PostMapping
    public ResponseEntity<?> processVoiceCommand(@RequestBody VoiceCommand command) {
        try {
            commandProcessor.processCommand(command.getText());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}