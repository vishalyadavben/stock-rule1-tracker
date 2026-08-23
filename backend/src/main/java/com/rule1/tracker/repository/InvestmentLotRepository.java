package com.rule1.tracker.repository;

import com.rule1.tracker.entity.InvestmentLot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvestmentLotRepository extends JpaRepository<InvestmentLot, Long> {
    List<InvestmentLot> findByUserId(Long userId);
    List<InvestmentLot> findByUserIdAndStockId(Long userId, Long stockId);
    List<InvestmentLot> findByUserIdAndStatusNot(Long userId, InvestmentLot.LotStatus status);
}
