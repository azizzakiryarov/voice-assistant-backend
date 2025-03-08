package com.voiceassistant.service;

import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.TodoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TodoItemService {

    private final TodoRepository todoRepository;

    public TodoItemService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    public List<TodoItem> getTodos() {
        return todoRepository.findAll();
    }
}
