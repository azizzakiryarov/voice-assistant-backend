package com.voiceassistant.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidDateGivesBadRequestWithReadableMessage() {
        DateTimeParseException invalidDate = catchThrowableOfType(
                DateTimeParseException.class, () -> LocalDate.parse("2026-13-40"));

        ResponseEntity<GlobalExceptionHandler.ErrorDetails> response = handler.handleDateTimeException(
                invalidDate, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("Ogiltigt datum");
    }
}
