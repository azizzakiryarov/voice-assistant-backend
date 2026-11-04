package com.voiceassistant.dto;

import lombok.Data;

@Data
public class GoogleTasksSyncResultDTO {
    private int importedCount;
    private int updatedCount;
    private int deletedCount;
    private int skippedCount;
}
