package com.rule1.tracker.repository;

import com.rule1.tracker.entity.InvestmentExit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvestmentExitRepository extends JpaRepository<InvestmentExit, Long> {
    List<InvestmentExit> findByLotId(Long lotId);
    List<InvestmentExit> findByLotIdIn(List<Long> lotIds);
}
