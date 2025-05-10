package com.voiceassistant.controller;

import com.voiceassistant.dto.MeetingRequestDTO;
import com.voiceassistant.dto.MeetingResponseDTO;
import com.voiceassistant.service.MeetingServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/voice-assistent")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingServiceImpl meetingServiceImpl;

    @GetMapping("/meetings")
    public ResponseEntity<List<MeetingResponseDTO>> getMeetings() {
        return ResponseEntity.ok(meetingServiceImpl.getAllMeetings());
    }

    @GetMapping("/meetings/{id}")
    public ResponseEntity<MeetingResponseDTO> getMeetingById(@PathVariable Long id) {
        return ResponseEntity.ok(meetingServiceImpl.getMeetingById(id));
    }

    @PostMapping("/meetings")
    public ResponseEntity<MeetingResponseDTO> createMeeting(@Valid @RequestBody MeetingRequestDTO meetingRequestDTO) {
        MeetingResponseDTO createdMeeting = meetingServiceImpl.createMeeting(meetingRequestDTO);
        return new ResponseEntity<>(createdMeeting, HttpStatus.CREATED);
    }

    @PutMapping("/meetings/{id}")
    public ResponseEntity<MeetingResponseDTO> updateMeeting(@PathVariable Long id, @Valid @RequestBody MeetingRequestDTO meetingRequestDTO) {
        MeetingResponseDTO updatedMeeting = meetingServiceImpl.updateMeeting(id, meetingRequestDTO);
        return ResponseEntity.ok(updatedMeeting);
    }

    @DeleteMapping("/meetings/{id}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable Long id) {
        meetingServiceImpl.deleteMeeting(id);
        return ResponseEntity.noContent().build();
    }
}