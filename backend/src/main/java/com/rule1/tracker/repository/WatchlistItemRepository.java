package com.rule1.tracker.repository;

import com.rule1.tracker.entity.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByUserId(Long userId);
    Optional<WatchlistItem> findByUserIdAndStockId(Long userId, Long stockId);
}
