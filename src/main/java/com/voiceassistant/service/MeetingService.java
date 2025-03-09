package com.voiceassistant.service;

import com.voiceassistant.dto.MeetingRequestDTO;
import com.voiceassistant.dto.MeetingResponseDTO;

import java.util.List;

public interface MeetingService {
    MeetingResponseDTO createMeeting(MeetingRequestDTO meetingRequestDTO);
    MeetingResponseDTO getMeetingById(Long id);
    List<MeetingResponseDTO> getAllMeetings();
    MeetingResponseDTO updateMeeting(Long id, MeetingRequestDTO meetingRequestDTO);
    void deleteMeeting(Long id);
}