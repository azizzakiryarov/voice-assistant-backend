package com.voiceassistant.repository;

import com.voiceassistant.model.TodoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TodoRepository extends JpaRepository<TodoItem, Long> {
    List<TodoItem> findAllByOwnerId(Long ownerId);

    @Query("""
            select todo from TodoItem todo
            where todo.owner.id = :ownerId
            order by
                case when todo.googlePosition is null then 1 else 0 end,
                todo.googleTaskListId asc,
                todo.googlePosition asc,
                todo.id asc
            """)
    List<TodoItem> findAllByOwnerIdSortedLikeGoogle(@Param("ownerId") Long ownerId);

    Optional<TodoItem> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<TodoItem> findByOwnerIdAndGoogleTaskListIdAndGoogleTaskId(Long ownerId, String googleTaskListId, String googleTaskId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);
}
