package com.rule1.tracker.repository;

import com.rule1.tracker.entity.UserNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserNoteRepository extends JpaRepository<UserNote, Long> {
    List<UserNote> findByUserIdOrderByCreatedAtDesc(Long userId);
}
