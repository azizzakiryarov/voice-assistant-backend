package com.voiceassistant.repository;

import com.voiceassistant.model.TodoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TodoRepository extends JpaRepository<TodoItem, Long> {
    List<TodoItem> findAllByOwnerId(Long ownerId);

    Optional<TodoItem> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);
}
