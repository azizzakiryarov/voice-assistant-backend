package com.voiceassistant.controller;

import com.voiceassistant.model.VoiceCommand;
import com.voiceassistant.service.CommandProcessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice-assistent")
@RequiredArgsConstructor
public class VoiceCommandController {
    private final CommandProcessorService commandProcessor;

    @PostMapping("/text")
    public ResponseEntity<String> processVoiceCommand(@RequestBody VoiceCommand command) {
        try {
            commandProcessor.processCommand(command.getText());
            return ResponseEntity.ok("Your text message has been processed");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred: " + e);
        }
    }

    @PostMapping("/voice")
    public ResponseEntity<String> translateAndSave(@RequestParam("file") MultipartFile file) {
        try {
            commandProcessor.translateAndSave(file);
            return ResponseEntity.ok("Translation and save successful: " + file.getOriginalFilename());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("An error occurred: " + e.getMessage());
        }
    }
}