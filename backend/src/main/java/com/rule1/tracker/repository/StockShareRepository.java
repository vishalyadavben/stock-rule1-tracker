package com.rule1.tracker.repository;

import com.rule1.tracker.entity.StockShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockShareRepository extends JpaRepository<StockShare, Long> {
    List<StockShare> findByStockIdAndOwnerUserId(Long stockId, Long ownerUserId);
    Optional<StockShare> findByStockIdAndOwnerUserIdAndSharedWithEmail(Long stockId, Long ownerUserId, String email);
    Optional<StockShare> findByStockIdAndOwnerUserIdAndSharedWithUserId(Long stockId, Long ownerUserId, Long sharedWithUserId);
    List<StockShare> findBySharedWithUserId(Long sharedWithUserId);
    List<StockShare> findBySharedWithEmailAndSharedWithUserIdIsNull(String email);
}
