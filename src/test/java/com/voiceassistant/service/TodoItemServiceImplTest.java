package com.voiceassistant.service;

import com.voiceassistant.dto.TodoItemRequestDTO;
import com.voiceassistant.dto.TodoItemResponseDTO;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TodoItemServiceImplTest {

    private TodoRepository todoRepository;
    private TodoItemServiceImpl todoItemService;

    @BeforeEach
    void setUp() {
        todoRepository = mock(TodoRepository.class);
        todoItemService = new TodoItemServiceImpl(todoRepository, new ModelMapper());
    }

    @Test
    void updateTodoItemAllowsCompletedOnlyUpdate() {
        TodoItem existing = new TodoItem();
        existing.setId(7L);
        existing.setDescription("köpa kaffe");
        existing.setDueDate(LocalDate.of(2026, 6, 13));
        existing.setCompleted(false);

        TodoItemRequestDTO request = new TodoItemRequestDTO();
        request.setCompleted(true);

        when(todoRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(todoRepository.save(existing)).thenReturn(existing);

        TodoItemResponseDTO response = todoItemService.updateTodoItem(7L, request);

        assertThat(response.getDescription()).isEqualTo("köpa kaffe");
        assertThat(response.getDueDate()).isEqualTo(LocalDate.of(2026, 6, 13));
        assertThat(response.isCompleted()).isTrue();
        verify(todoRepository).save(existing);
    }
}
