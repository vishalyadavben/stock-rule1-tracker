package com.rule1.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "checklist_items")
@Data
public class ChecklistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category;
    private String prompt;

    @Column(name = "display_order")
    private Integer displayOrder;
}
