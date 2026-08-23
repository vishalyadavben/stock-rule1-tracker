package com.rule1.tracker.repository;

import com.rule1.tracker.entity.StickerPriceCalc;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StickerPriceCalcRepository extends JpaRepository<StickerPriceCalc, Long> {
    List<StickerPriceCalc> findByUserIdAndStockIdOrderByCalculatedAtDesc(Long userId, Long stockId);
}
