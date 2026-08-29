package com.rule1.tracker.service;

import com.rule1.tracker.entity.NoteShare;
import com.rule1.tracker.entity.User;
import com.rule1.tracker.entity.UserNote;
import com.rule1.tracker.repository.NoteShareRepository;
import com.rule1.tracker.repository.UserNoteRepository;
import com.rule1.tracker.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** Governs sharing of a single personal note with another user by email, mirroring
 *  SharingService's pattern for stock analysis. */
@Service
public class NoteSharingService {

    private final NoteShareRepository shareRepository;
    private final UserNoteRepository noteRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public NoteSharingService(NoteShareRepository shareRepository, UserNoteRepository noteRepository,
                               UserRepository userRepository, EmailService emailService) {
        this.shareRepository = shareRepository;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public NoteShare shareNote(Long ownerUserId, Long noteId, String email, NoteShare.Permission permission) {
        UserNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));
        if (!note.getUserId().equals(ownerUserId)) {
            throw new RuntimeException("Not authorized for this note");
        }

        String normalizedEmail = email.trim().toLowerCase();
        NoteShare share = shareRepository.findByNoteIdAndSharedWithEmail(noteId, normalizedEmail)
                .orElseGet(NoteShare::new);

        boolean isNew = share.getId() == null;
        share.setNoteId(noteId);
        share.setOwnerUserId(ownerUserId);
        share.setSharedWithEmail(normalizedEmail);
        share.setPermission(permission);
        if (share.getCreatedAt() == null) share.setCreatedAt(LocalDateTime.now());

        userRepository.findByEmail(normalizedEmail).ifPresent(u -> share.setSharedWithUserId(u.getId()));

        NoteShare saved = shareRepository.save(share);

        if (isNew) {
            User owner = userRepository.findById(ownerUserId).orElse(null);
            if (owner != null) {
                emailService.sendNoteShareInviteEmail(normalizedEmail, owner.getEmail(), permission.name());
            }
        }
        return saved;
    }

    public List<NoteShare> listSharesForNote(Long ownerUserId, Long noteId) {
        UserNote note = noteRepository.findById(noteId).orElseThrow(() -> new RuntimeException("Note not found"));
        if (!note.getUserId().equals(ownerUserId)) {
            throw new RuntimeException("Not authorized for this note");
        }
        return shareRepository.findByNoteId(noteId);
    }

    public List<NoteShare> listSharedWithMe(Long userId) {
        return shareRepository.findBySharedWithUserId(userId);
    }

    public void revokeShare(Long ownerUserId, Long shareId) {
        NoteShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new RuntimeException("Share not found"));
        if (!share.getOwnerUserId().equals(ownerUserId)) {
            throw new RuntimeException("Not authorized to revoke this share");
        }
        shareRepository.delete(share);
    }

    /** Owners always have full access; everyone else needs a matching NoteShare at sufficient
     *  permission (VIEW satisfied by VIEW or EDIT; EDIT requires EDIT). */
    public void requireAccess(Long requesterId, UserNote note, NoteShare.Permission required) {
        if (note.getUserId().equals(requesterId)) return;
        NoteShare share = shareRepository.findByNoteIdAndSharedWithUserId(note.getId(), requesterId)
                .orElseThrow(() -> new RuntimeException("This note hasn't been shared with you"));
        if (required == NoteShare.Permission.EDIT && share.getPermission() != NoteShare.Permission.EDIT) {
            throw new RuntimeException("You only have view access to this note");
        }
    }

    public void linkPendingSharesForNewUser(User newUser) {
        List<NoteShare> pending = shareRepository.findBySharedWithEmailAndSharedWithUserIdIsNull(
                newUser.getEmail().trim().toLowerCase());
        for (NoteShare share : pending) {
            share.setSharedWithUserId(newUser.getId());
            shareRepository.save(share);
        }
    }
}
