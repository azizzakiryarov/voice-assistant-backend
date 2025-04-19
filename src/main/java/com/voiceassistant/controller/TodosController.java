package com.voiceassistant.controller;

import com.voiceassistant.dto.TodoItemRequestDTO;
import com.voiceassistant.dto.TodoItemResponseDTO;
import com.voiceassistant.service.TodoItemServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/voice-assistent")
@RequiredArgsConstructor
public class TodosController {

    private final TodoItemServiceImpl todoItemServiceImpl;

    @GetMapping()
    public ResponseEntity<List<TodoItemResponseDTO>> getAllTodoItems() {
        return ResponseEntity.ok(todoItemServiceImpl.getAllTodoItems());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TodoItemResponseDTO> getTodoById(@PathVariable Long id) {
        return ResponseEntity.ok(todoItemServiceImpl.getTodoItemById(id));
    }

    @PostMapping
    public ResponseEntity<TodoItemResponseDTO> createTodoItem(@Valid @RequestBody TodoItemRequestDTO todoItemRequestDTO) {
        TodoItemResponseDTO createdUser = todoItemServiceImpl.createTodoItem(todoItemRequestDTO);
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TodoItemResponseDTO> updateTodoItem(@PathVariable Long id, @Valid @RequestBody TodoItemRequestDTO todoItemRequestDTO) {
        TodoItemResponseDTO updatedUser = todoItemServiceImpl.updateTodoItem(id, todoItemRequestDTO);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodoItem(@PathVariable Long id) {
        todoItemServiceImpl.deleteTodoItem(id);
        return ResponseEntity.noContent().build();
    }
}