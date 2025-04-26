package com.voiceassistant.service;

import com.voiceassistant.dto.TodoItemRequestDTO;
import com.voiceassistant.dto.TodoItemResponseDTO;
import com.voiceassistant.exception.ResourceNotFoundException;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.TodoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TodoItemServiceImpl implements TodoItemService {

    public static final String TODO_ITEM_NOT_FOUND_WITH_ID = "TodoItem not found with id: ";
    private final TodoRepository todoRepository;
    private final ModelMapper modelMapper;

    public TodoItemServiceImpl(TodoRepository todoRepository, ModelMapper modelMapper) {
        this.todoRepository = todoRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    public TodoItemResponseDTO createTodoItem(TodoItemRequestDTO todoItemRequestDTO) {
        TodoItem todoItem = modelMapper.map(todoItemRequestDTO, TodoItem.class);
        if (todoItem.getDescription() == null) {
            throw new IllegalArgumentException("Title are required fields");
        }
        TodoItem savedTodoItem = todoRepository.save(todoItem);
        return modelMapper.map(savedTodoItem, TodoItemResponseDTO.class);
    }

    @Override
    public TodoItemResponseDTO getTodoItemById(Long id) {
        TodoItem todoItem = todoRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TODO_ITEM_NOT_FOUND_WITH_ID + id));
        return modelMapper.map(todoItem, TodoItemResponseDTO.class);
    }

    @Override
    public List<TodoItemResponseDTO> getAllTodoItems() {
        return todoRepository.findAll()
                .stream()
                .map(todoItem -> modelMapper.map(todoItem, TodoItemResponseDTO.class))
                .toList();
    }

    @Override
    public TodoItemResponseDTO updateTodoItem(Long id, TodoItemRequestDTO todoItemRequestDTO) {
        TodoItem existingTodoItem = todoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TODO_ITEM_NOT_FOUND_WITH_ID + id));
        existingTodoItem.setCompleted(todoItemRequestDTO.isCompleted());
        TodoItem updatedTodoItem = todoRepository.save(existingTodoItem);
        return modelMapper.map(updatedTodoItem, TodoItemResponseDTO.class);
    }

    @Override
    public void deleteTodoItem(Long id) {
        if (!todoRepository.existsById(id)) {
            throw new ResourceNotFoundException(TODO_ITEM_NOT_FOUND_WITH_ID + id);
        }
        todoRepository.deleteById(id);
    }
}