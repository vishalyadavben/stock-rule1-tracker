package com.rule1.tracker.repository;

import com.rule1.tracker.entity.NoteShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteShareRepository extends JpaRepository<NoteShare, Long> {
    List<NoteShare> findByNoteId(Long noteId);
    List<NoteShare> findBySharedWithUserId(Long userId);
    Optional<NoteShare> findByNoteIdAndSharedWithEmail(Long noteId, String email);
    Optional<NoteShare> findByNoteIdAndSharedWithUserId(Long noteId, Long sharedWithUserId);
    List<NoteShare> findBySharedWithEmailAndSharedWithUserIdIsNull(String email);
}
