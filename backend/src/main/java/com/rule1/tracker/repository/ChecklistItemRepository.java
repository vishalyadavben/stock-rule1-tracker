package com.rule1.tracker.repository;

import com.rule1.tracker.entity.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
}
