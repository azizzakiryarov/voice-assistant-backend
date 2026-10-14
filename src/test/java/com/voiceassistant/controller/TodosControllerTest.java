package com.voiceassistant.controller;

import com.voiceassistant.exception.GlobalExceptionHandler;
import com.voiceassistant.integration.google.service.GoogleTasksService;
import com.voiceassistant.service.TodoItemServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TodosControllerTest {

    private MockMvc mockMvc;
    private TodoItemServiceImpl todoItemService;

    @BeforeEach
    void setUp() {
        todoItemService = mock(TodoItemServiceImpl.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TodosController(todoItemService, mock(GoogleTasksService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidTodoInputReturnsBadRequestInsteadOfServerError() throws Exception {
        when(todoItemService.updateTodoItem(any(), any()))
                .thenThrow(new IllegalArgumentException("Description is required"));

        mockMvc.perform(put("/api/voice-assistent/todos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Description is required"));
    }
}
