package com.rule1.tracker.controller;

import com.rule1.tracker.entity.*;
import com.rule1.tracker.repository.*;
import com.rule1.tracker.security.CurrentUser;
import com.rule1.tracker.service.CalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/score")
public class ScoreController {

    private final StockRepository stockRepository;
    private final BigFiveMetricRepository bigFiveMetricRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistResponseRepository checklistResponseRepository;
    private final StickerPriceCalcRepository stickerPriceCalcRepository;
    private final CalculationService calculationService;

    public ScoreController(StockRepository stockRepository, BigFiveMetricRepository bigFiveMetricRepository,
                            ChecklistItemRepository checklistItemRepository,
                            ChecklistResponseRepository checklistResponseRepository,
                            StickerPriceCalcRepository stickerPriceCalcRepository,
                            CalculationService calculationService) {
        this.stockRepository = stockRepository;
        this.bigFiveMetricRepository = bigFiveMetricRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.checklistResponseRepository = checklistResponseRepository;
        this.stickerPriceCalcRepository = stickerPriceCalcRepository;
        this.calculationService = calculationService;
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<CalculationService.ScoreResult> score(@PathVariable String ticker) {
        Long userId = CurrentUser.id();
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        List<BigFiveMetric> yearly = bigFiveMetricRepository.findByStockIdOrderByFiscalYearAsc(stock.getId());

        Map<String, Boolean> passFail = new java.util.HashMap<>();
        passFail.put("sales", pass10yr(yearly, "sales"));
        passFail.put("eps", pass10yr(yearly, "eps"));
        passFail.put("equity", pass10yr(yearly, "equity"));
        passFail.put("freeCashFlow", pass10yr(yearly, "freeCashFlow"));
        boolean roicPass = yearly.stream().anyMatch(m ->
                m.getRoicPct() != null && m.getRoicPct().compareTo(BigDecimal.TEN) >= 0);
        passFail.put("roic", roicPass);

        long totalChecklist = checklistItemRepository.count();
        long checkedCount = checklistResponseRepository.findByUserIdAndStockId(userId, stock.getId())
                .stream().filter(r -> Boolean.TRUE.equals(r.getIsChecked())).count();

        boolean belowMos = false;
        var latestCalcs = stickerPriceCalcRepository.findByUserIdAndStockIdOrderByCalculatedAtDesc(userId, stock.getId());
        if (!latestCalcs.isEmpty() && stock.getLastPrice() != null) {
            belowMos = stock.getLastPrice().compareTo(latestCalcs.get(0).getMarginOfSafetyPrice()) <= 0;
        }

        var result = calculationService.computeBusinessScore(passFail, totalChecklist, checkedCount, belowMos);
        return ResponseEntity.ok(result);
    }

    private boolean pass10yr(List<BigFiveMetric> yearly, String field) {
        var rates = calculationService.computeGrowthRates(yearly, field);
        return calculationService.passesTenPercentRule(rates.get("10yr"));
    }
}
