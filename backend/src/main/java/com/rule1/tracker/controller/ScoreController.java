package com.rule1.tracker.controller;

import com.rule1.tracker.entity.*;
import com.rule1.tracker.repository.*;
import com.rule1.tracker.security.CurrentUser;
import com.rule1.tracker.service.CalculationService;
import com.rule1.tracker.service.SharingService;
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
    private final SharingService sharingService;

    public ScoreController(StockRepository stockRepository, BigFiveMetricRepository bigFiveMetricRepository,
                            ChecklistItemRepository checklistItemRepository,
                            ChecklistResponseRepository checklistResponseRepository,
                            StickerPriceCalcRepository stickerPriceCalcRepository,
                            CalculationService calculationService, SharingService sharingService) {
        this.stockRepository = stockRepository;
        this.bigFiveMetricRepository = bigFiveMetricRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.checklistResponseRepository = checklistResponseRepository;
        this.stickerPriceCalcRepository = stickerPriceCalcRepository;
        this.calculationService = calculationService;
        this.sharingService = sharingService;
    }

    /** ownerId (optional): the score for someone else's shared analysis of this stock,
     *  if they've shared it with you at least at VIEW permission. */
    @GetMapping("/{ticker}")
    public ResponseEntity<?> score(@PathVariable String ticker, @RequestParam(required = false) Long ownerId) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        Long userId;
        try {
            userId = sharingService.resolveEffectiveOwner(CurrentUser.id(), ownerId, stock.getId(), StockShare.Permission.VIEW);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }

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
