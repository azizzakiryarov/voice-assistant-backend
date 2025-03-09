package com.voiceassistant.service;

import com.voiceassistant.dto.TodoItemRequestDTO;
import com.voiceassistant.dto.TodoItemResponseDTO;
import com.voiceassistant.exception.ResourceNotFoundException;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.TodoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TodoItemServiceImpl implements TodoItemService {

    private final TodoRepository todoRepository;
    private final ModelMapper modelMapper;

    public TodoItemServiceImpl(TodoRepository todoRepository, ModelMapper modelMapper) {
        this.todoRepository = todoRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    public TodoItemResponseDTO createTodoItem(TodoItemRequestDTO todoItemRequestDTO) {
        TodoItem todoItem = modelMapper.map(todoItemRequestDTO, TodoItem.class);
        TodoItem savedTodoItem = todoRepository.save(todoItem);
        return modelMapper.map(savedTodoItem, TodoItemResponseDTO.class);
    }

    @Override
    public TodoItemResponseDTO getTodoItemById(Long id) {
        TodoItem todoItem = todoRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TodoItem not found with id: " + id));
        return modelMapper.map(todoItem, TodoItemResponseDTO.class);
    }

    @Override
    public List<TodoItemResponseDTO> getAllTodoItems() {
        return todoRepository.findAll().stream()
                .map(todoItem -> modelMapper.map(todoItem, TodoItemResponseDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public TodoItemResponseDTO updateTodoItem(Long id, TodoItemRequestDTO todoItemRequestDTO) {
        TodoItem existingTodoItem = todoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TodoItem not found with id: " + id));

        // Update fields
        modelMapper.map(todoItemRequestDTO, existingTodoItem);
        existingTodoItem.setId(id); // Ensure ID doesn't change

        TodoItem updatedTodoItem = todoRepository.save(existingTodoItem);
        return modelMapper.map(updatedTodoItem, TodoItemResponseDTO.class);
    }

    @Override
    public void deleteTodoItem(Long id) {
        if(!todoRepository.existsById(id)) {
            throw new ResourceNotFoundException("TodoItem not found with id: " + id);
        }
        todoRepository.deleteById(id);
    }
}