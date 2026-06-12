package com.voiceassistant.service;

import com.voiceassistant.dto.MeetingRequestDTO;
import com.voiceassistant.dto.MeetingResponseDTO;
import com.voiceassistant.exception.ResourceNotFoundException;
import com.voiceassistant.model.AppUser;
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
    private final AppUserService appUserService;

    public MeetingServiceImpl(MeetingRepository meetingRepository, ModelMapper modelMapper, AppUserService appUserService) {
        this.meetingRepository = meetingRepository;
        this.modelMapper = modelMapper;
        this.appUserService = appUserService;
    }

    @Override
    public MeetingResponseDTO createMeeting(MeetingRequestDTO meetingRequestDTO) {
        AppUser owner = appUserService.getCurrentUser();
        Meeting meeting = modelMapper.map(meetingRequestDTO, Meeting.class);
        if (meeting.getTitle() == null || meeting.getStartTimestamp() == null || meeting.getEndTimestamp() == null) {
            throw new IllegalArgumentException("Title, startTimestamp and endTimestamp are required fields");
        }
        meeting.setOwner(owner);
        Meeting savedMeeting = meetingRepository.save(meeting);
        return modelMapper.map(savedMeeting, MeetingResponseDTO.class);
    }

    @Override
    public MeetingResponseDTO getMeetingById(Long id) {
        AppUser owner = appUserService.getCurrentUser();
        Meeting meeting = meetingRepository
                .findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(MEETING_NOT_FOUND_WITH_ID + id));
        return modelMapper.map(meeting, MeetingResponseDTO.class);
    }

    @Override
    public List<MeetingResponseDTO> getAllMeetings() {
        AppUser owner = appUserService.getCurrentUser();
        return meetingRepository.findAllByOwnerId(owner.getId())
                .stream()
                .map(meeting -> modelMapper.map(meeting, MeetingResponseDTO.class))
                .toList();
    }

    @Override
    public MeetingResponseDTO updateMeeting(Long id, MeetingRequestDTO meetingRequestDTO) {
        AppUser owner = appUserService.getCurrentUser();
        Meeting existingMeeting = meetingRepository
                .findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(MEETING_NOT_FOUND_WITH_ID + id));

        //Update fields
        modelMapper.map(meetingRequestDTO, existingMeeting);
        existingMeeting.setId(id); // Ensure ID doesn't change
        existingMeeting.setOwner(owner);

        Meeting updatedMeeting = meetingRepository.save(existingMeeting);
        return modelMapper.map(updatedMeeting, MeetingResponseDTO.class);
    }

    @Override
    public void deleteMeeting(Long id) {
        AppUser owner = appUserService.getCurrentUser();
        if(!meetingRepository.existsByIdAndOwnerId(id, owner.getId())) {
            throw new ResourceNotFoundException(MEETING_NOT_FOUND_WITH_ID + id);
        }
        meetingRepository.deleteById(id);
    }
}
