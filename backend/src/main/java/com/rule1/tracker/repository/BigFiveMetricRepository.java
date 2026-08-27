package com.rule1.tracker.repository;

import com.rule1.tracker.entity.BigFiveMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BigFiveMetricRepository extends JpaRepository<BigFiveMetric, Long> {
    List<BigFiveMetric> findByStockIdOrderByFiscalYearAsc(Long stockId);
    List<BigFiveMetric> findByStockIdAndSourceOrderByFiscalYearAsc(Long stockId, BigFiveMetric.Source source);
    java.util.Optional<BigFiveMetric> findByStockIdAndFiscalYearAndSource(Long stockId, Integer fiscalYear, BigFiveMetric.Source source);
}
