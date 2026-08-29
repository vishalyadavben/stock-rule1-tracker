package com.rule1.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "note_shares")
@Data
public class NoteShare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "shared_with_email", nullable = false)
    private String sharedWithEmail;

    @Column(name = "shared_with_user_id")
    private Long sharedWithUserId;

    @Enumerated(EnumType.STRING)
    private Permission permission = Permission.VIEW;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum Permission { VIEW, EDIT }
}
