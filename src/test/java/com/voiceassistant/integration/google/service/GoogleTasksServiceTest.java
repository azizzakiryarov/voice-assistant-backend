package com.voiceassistant.integration.google.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTasksServiceTest {

    @Test
    void buildDescriptionIncludesGoogleTaskNotesBelowTitle() {
        String description = GoogleTasksService.buildDescription(
                "Book dentist",
                "Call before lunch and ask about next week"
        );

        assertThat(description).isEqualTo("Book dentist\nCall before lunch and ask about next week");
    }

    @Test
    void buildDescriptionUsesOnlyTitleWhenGoogleTaskNotesAreBlank() {
        String description = GoogleTasksService.buildDescription("Book dentist", "   ");

        assertThat(description).isEqualTo("Book dentist");
    }
}
