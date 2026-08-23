package com.rule1.tracker.repository;

import com.rule1.tracker.entity.ChecklistResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChecklistResponseRepository extends JpaRepository<ChecklistResponse, Long> {
    List<ChecklistResponse> findByUserIdAndStockId(Long userId, Long stockId);
}
