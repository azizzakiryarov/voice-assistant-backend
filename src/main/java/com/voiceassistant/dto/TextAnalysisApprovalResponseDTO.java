package com.voiceassistant.dto;

import java.util.List;

public record TextAnalysisApprovalResponseDTO(
        List<MeetingResponseDTO> createdEvents,
        List<TodoItemResponseDTO> createdTodos,
        int googleCalendarSyncedCount,
        int googleTasksSyncedCount
) {
}
