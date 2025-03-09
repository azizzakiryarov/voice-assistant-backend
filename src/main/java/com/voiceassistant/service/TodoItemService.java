package com.voiceassistant.service;

import com.voiceassistant.dto.TodoItemRequestDTO;
import com.voiceassistant.dto.TodoItemResponseDTO;

import java.util.List;

public interface TodoItemService {
    TodoItemResponseDTO createTodoItem(TodoItemRequestDTO todoItemRequestDTO);
    TodoItemResponseDTO getTodoItemById(Long id);
    List<TodoItemResponseDTO> getAllTodoItems();
    TodoItemResponseDTO updateTodoItem(Long id, TodoItemRequestDTO todoItemRequestDTO);
    void deleteTodoItem(Long id);
}