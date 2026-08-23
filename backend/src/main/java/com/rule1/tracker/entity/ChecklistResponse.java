package com.rule1.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "checklist_responses")
@Data
public class ChecklistResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "checklist_item_id", nullable = false)
    private Long checklistItemId;

    @Column(name = "is_checked")
    private Boolean isChecked = false;

    @Column(name = "free_text")
    private String freeText;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
