package com.voiceassistant.service;

import com.voiceassistant.dto.TodoItemRequestDTO;
import com.voiceassistant.dto.TodoItemResponseDTO;
import com.voiceassistant.exception.ResourceNotFoundException;
import com.voiceassistant.model.AppUser;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.TodoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TodoItemServiceImpl implements TodoItemService {

    public static final String TODO_ITEM_NOT_FOUND_WITH_ID = "TodoItem not found with id: ";
    private final TodoRepository todoRepository;
    private final ModelMapper modelMapper;
    private final AppUserService appUserService;

    public TodoItemServiceImpl(TodoRepository todoRepository, ModelMapper modelMapper, AppUserService appUserService) {
        this.todoRepository = todoRepository;
        this.modelMapper = modelMapper;
        this.appUserService = appUserService;
    }

    @Override
    public TodoItemResponseDTO createTodoItem(TodoItemRequestDTO todoItemRequestDTO) {
        AppUser owner = appUserService.getCurrentUser();
        TodoItem todoItem = modelMapper.map(todoItemRequestDTO, TodoItem.class);
        if (todoItem.getDescription() == null) {
            throw new IllegalArgumentException("Title are required fields");
        }
        if (todoItem.getDueDate() == null) {
            todoItem.setDueDate(LocalDate.now());
        }
        todoItem.setOwner(owner);
        todoItem.setSyncStatus("LOCAL");
        TodoItem savedTodoItem = todoRepository.save(todoItem);
        return modelMapper.map(savedTodoItem, TodoItemResponseDTO.class);
    }

    @Override
    public TodoItemResponseDTO getTodoItemById(Long id) {
        AppUser owner = appUserService.getCurrentUser();
        TodoItem todoItem = todoRepository
                .findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(TODO_ITEM_NOT_FOUND_WITH_ID + id));
        return modelMapper.map(todoItem, TodoItemResponseDTO.class);
    }

    @Override
    public List<TodoItemResponseDTO> getAllTodoItems() {
        AppUser owner = appUserService.getCurrentUser();
        return todoRepository.findAllByOwnerId(owner.getId())
                .stream()
                .map(todoItem -> modelMapper.map(todoItem, TodoItemResponseDTO.class))
                .toList();
    }

    @Override
    public TodoItemResponseDTO updateTodoItem(Long id, TodoItemRequestDTO todoItemRequestDTO) {
        AppUser owner = appUserService.getCurrentUser();
        TodoItem existingTodoItem = todoRepository.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(TODO_ITEM_NOT_FOUND_WITH_ID + id));
        if (todoItemRequestDTO.getDescription() != null && !todoItemRequestDTO.getDescription().isBlank()) {
            existingTodoItem.setDescription(todoItemRequestDTO.getDescription());
        }
        if (todoItemRequestDTO.getDueDate() != null) {
            existingTodoItem.setDueDate(todoItemRequestDTO.getDueDate());
        }
        existingTodoItem.setCompleted(todoItemRequestDTO.isCompleted());
        TodoItem updatedTodoItem = todoRepository.save(existingTodoItem);
        return modelMapper.map(updatedTodoItem, TodoItemResponseDTO.class);
    }

    @Override
    public void deleteTodoItem(Long id) {
        AppUser owner = appUserService.getCurrentUser();
        if (!todoRepository.existsByIdAndOwnerId(id, owner.getId())) {
            throw new ResourceNotFoundException(TODO_ITEM_NOT_FOUND_WITH_ID + id);
        }
        todoRepository.deleteById(id);
    }
}
