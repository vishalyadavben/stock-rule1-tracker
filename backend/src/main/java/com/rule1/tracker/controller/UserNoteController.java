package com.rule1.tracker.controller;

import com.rule1.tracker.entity.NoteShare;
import com.rule1.tracker.entity.User;
import com.rule1.tracker.entity.UserNote;
import com.rule1.tracker.repository.UserNoteRepository;
import com.rule1.tracker.repository.UserRepository;
import com.rule1.tracker.security.CurrentUser;
import com.rule1.tracker.service.NoteSharingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Personal freeform notes, visible to a user across every login — not tied to any stock.
 *  Can optionally be shared with another user by email, with VIEW or EDIT permission. */
@RestController
@RequestMapping("/api/notes")
public class UserNoteController {

    private final UserNoteRepository repository;
    private final UserRepository userRepository;
    private final NoteSharingService noteSharingService;

    public UserNoteController(UserNoteRepository repository, UserRepository userRepository,
                               NoteSharingService noteSharingService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.noteSharingService = noteSharingService;
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

    public record UpdateRequest(String content) {}

    /** Edits a note's content. Works on your own notes always; on a note shared with you only
     *  if you were granted EDIT permission — writes go to the SAME row, so the owner sees your
     *  change too, not a private copy. */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateRequest req) {
        UserNote note = repository.findById(id).orElse(null);
        if (note == null) return ResponseEntity.notFound().build();
        try {
            noteSharingService.requireAccess(CurrentUser.id(), note, NoteShare.Permission.EDIT);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
        note.setContent(req.content());
        return ResponseEntity.ok(repository.save(note));
    }

    /** Deletion is restricted to the note's actual owner, even for someone granted EDIT access —
     *  shared editing lets a collaborator update content, not permanently remove someone else's
     *  personal note. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.findById(id).ifPresent(note -> {
            if (note.getUserId().equals(CurrentUser.id())) {
                repository.delete(note);
            }
        });
        return ResponseEntity.noContent().build();
    }

    public record ShareRequest(String email, String permission) {}

    @PostMapping("/{id}/share")
    public ResponseEntity<?> share(@PathVariable Long id, @RequestBody ShareRequest req) {
        NoteShare.Permission permission;
        try {
            permission = NoteShare.Permission.valueOf(req.permission().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "permission must be VIEW or EDIT"));
        }
        if (req.email() == null || req.email().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        try {
            return ResponseEntity.ok(noteSharingService.shareNote(CurrentUser.id(), id, req.email(), permission));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    public record ShareView(Long id, String email, String permission, boolean hasAccount, String createdAt) {}

    @GetMapping("/{id}/shares")
    public ResponseEntity<?> shares(@PathVariable Long id) {
        try {
            List<ShareView> views = noteSharingService.listSharesForNote(CurrentUser.id(), id).stream()
                    .map(s -> new ShareView(s.getId(), s.getSharedWithEmail(), s.getPermission().name(),
                            s.getSharedWithUserId() != null, String.valueOf(s.getCreatedAt())))
                    .toList();
            return ResponseEntity.ok(views);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/shares/{shareId}")
    public ResponseEntity<?> revoke(@PathVariable Long shareId) {
        try {
            noteSharingService.revokeShare(CurrentUser.id(), shareId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    public record SharedNoteView(Long noteId, String content, String ownerEmail, String permission, String createdAt) {}

    /** Notes other users have shared with the current user. */
    @GetMapping("/shared-with-me")
    public ResponseEntity<List<SharedNoteView>> sharedWithMe() {
        List<SharedNoteView> views = noteSharingService.listSharedWithMe(CurrentUser.id()).stream()
                .map(s -> {
                    UserNote note = repository.findById(s.getNoteId()).orElse(null);
                    String ownerEmail = userRepository.findById(s.getOwnerUserId())
                            .map(User::getEmail).orElse("?");
                    return new SharedNoteView(
                            s.getNoteId(), note != null ? note.getContent() : "(deleted)",
                            ownerEmail, s.getPermission().name(), String.valueOf(s.getCreatedAt())
                    );
                })
                .toList();
        return ResponseEntity.ok(views);
    }
}
