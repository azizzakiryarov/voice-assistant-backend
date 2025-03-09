package com.voiceassistant.service;

import com.voiceassistant.dto.MeetingRequestDTO;
import com.voiceassistant.dto.MeetingResponseDTO;
import com.voiceassistant.exception.ResourceNotFoundException;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.repository.MeetingRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MeetingServiceImpl implements MeetingService {

    public static final String MEETING_NOT_FOUND_WITH_ID = "Meeting not found with id: ";
    private final MeetingRepository meetingRepository;
    private final ModelMapper modelMapper;

    public MeetingServiceImpl(MeetingRepository meetingRepository, ModelMapper modelMapper) {
        this.meetingRepository = meetingRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    public MeetingResponseDTO createMeeting(MeetingRequestDTO meetingRequestDTO) {
        Meeting meeting = modelMapper.map(meetingRequestDTO, Meeting.class);
        if (meeting.getTitle() == null || meeting.getStartTimestamp() == null || meeting.getEndTimestamp() == null) {
            throw new IllegalArgumentException("Title, startTimestamp and endTimestamp are required fields");
        }
        Meeting savedMeeting = meetingRepository.save(meeting);
        return modelMapper.map(savedMeeting, MeetingResponseDTO.class);
    }

    @Override
    public MeetingResponseDTO getMeetingById(Long id) {
        Meeting meeting = meetingRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(MEETING_NOT_FOUND_WITH_ID + id));
        return modelMapper.map(meeting, MeetingResponseDTO.class);
    }

    @Override
    public List<MeetingResponseDTO> getAllMeetings() {
        return meetingRepository.findAll()
                .stream()
                .map(meeting -> modelMapper.map(meeting, MeetingResponseDTO.class))
                .toList();
    }

    @Override
    public MeetingResponseDTO updateMeeting(Long id, MeetingRequestDTO meetingRequestDTO) {
        Meeting existingMeeting = meetingRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(MEETING_NOT_FOUND_WITH_ID + id));

        //Update fields
        modelMapper.map(meetingRequestDTO, existingMeeting);
        existingMeeting.setId(id); // Ensure ID doesn't change

        Meeting updatedMeeting = meetingRepository.save(existingMeeting);
        return modelMapper.map(updatedMeeting, MeetingResponseDTO.class);
    }

    @Override
    public void deleteMeeting(Long id) {
        if(!meetingRepository.existsById(id)) {
            throw new ResourceNotFoundException(MEETING_NOT_FOUND_WITH_ID + id);
        }
        meetingRepository.deleteById(id);
    }
}