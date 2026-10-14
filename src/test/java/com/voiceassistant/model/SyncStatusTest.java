package com.voiceassistant.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SyncStatusTest {

    @Test
    void namesMatchValuesStoredInDatabaseAndReadByFrontend() {
        assertThat(Arrays.stream(SyncStatus.values()).map(Enum::name))
                .containsExactly("LOCAL", "GOOGLE_TASKS_CREATED", "GOOGLE_TASKS_IMPORTED");
    }
}
