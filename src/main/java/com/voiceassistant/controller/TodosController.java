package com.voiceassistant.controller;

import com.voiceassistant.model.TodoItem;
import com.voiceassistant.service.TodoItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodosController {

    private final TodoItemService todoItemService;

    @GetMapping()
    public ResponseEntity<List<TodoItem>> getTodos() {
        return ResponseEntity.ok(todoItemService.getTodos());
    }
}