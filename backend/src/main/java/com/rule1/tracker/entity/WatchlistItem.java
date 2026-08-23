package com.rule1.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist_items")
@Data
public class WatchlistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    private String notes;
}
