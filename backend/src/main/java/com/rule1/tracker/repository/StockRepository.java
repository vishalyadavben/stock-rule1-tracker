package com.rule1.tracker.repository;

import com.rule1.tracker.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByTicker(String ticker);
}
