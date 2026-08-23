package com.rule1.tracker.controller;

import com.rule1.tracker.entity.UserNote;
import com.rule1.tracker.repository.UserNoteRepository;
import com.rule1.tracker.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** Personal freeform notes, visible to a user across every login — not tied to any stock. */
@RestController
@RequestMapping("/api/notes")
public class UserNoteController {

    private final UserNoteRepository repository;

    public UserNoteController(UserNoteRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<UserNote>> list() {
        return ResponseEntity.ok(repository.findByUserIdOrderByCreatedAtDesc(CurrentUser.id()));
    }

    public record CreateRequest(String content) {}

    @PostMapping
    public ResponseEntity<UserNote> create(@RequestBody CreateRequest req) {
        UserNote note = new UserNote();
        note.setUserId(CurrentUser.id());
        note.setContent(req.content());
        note.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(repository.save(note));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.findById(id).ifPresent(note -> {
            if (note.getUserId().equals(CurrentUser.id())) {
                repository.delete(note);
            }
        });
        return ResponseEntity.noContent().build();
    }
}
