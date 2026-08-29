package com.rule1.tracker.controller;

import com.rule1.tracker.entity.BigFiveMetric;
import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.entity.StickerPriceCalc;
import com.rule1.tracker.entity.StockShare;
import com.rule1.tracker.repository.BigFiveMetricRepository;
import com.rule1.tracker.repository.StickerPriceCalcRepository;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.security.CurrentUser;
import com.rule1.tracker.service.CalculationService;
import com.rule1.tracker.service.SharingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sticker-price")
public class StickerPriceController {

    private final CalculationService calculationService;
    private final StickerPriceCalcRepository repository;
    private final StockRepository stockRepository;
    private final BigFiveMetricRepository bigFiveMetricRepository;
    private final SharingService sharingService;

    public StickerPriceController(CalculationService calculationService, StickerPriceCalcRepository repository,
                                   StockRepository stockRepository, BigFiveMetricRepository bigFiveMetricRepository,
                                   SharingService sharingService) {
        this.calculationService = calculationService;
        this.repository = repository;
        this.stockRepository = stockRepository;
        this.bigFiveMetricRepository = bigFiveMetricRepository;
        this.sharingService = sharingService;
    }

    /** Big Five itself is global per stock (not owner-scoped), so no sharing check needed here —
     *  anyone analyzing this ticker already sees the same fundamentals. */
    @GetMapping("/suggest/{ticker}")
    public ResponseEntity<?> suggest(@PathVariable String ticker, @RequestParam(defaultValue = "API") String source) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first"));
        BigFiveMetric.Source src;
        try {
            src = BigFiveMetric.Source.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("source must be API or MANUAL");
        }
        List<BigFiveMetric> yearly = bigFiveMetricRepository.findByStockIdAndSourceOrderByFiscalYearAsc(stock.getId(), src);
        var suggestion = calculationService.suggestStickerInputs(yearly);
        if (suggestion == null) {
            return ResponseEntity.status(404).body(
                    "No " + src + " Big Five data found for " + ticker + " yet — fetch or enter it first.");
        }
        return ResponseEntity.ok(suggestion);
    }

    public record StickerPriceRequest(
            String ticker, BigDecimal currentEps, BigDecimal estimatedGrowthPct,
            BigDecimal estimatedFuturePe, BigDecimal minAcceptableReturnPct, Integer yearsToHold, Long ownerId
    ) {}

    /** ownerId (optional): saves this calculation under someone else's analysis instead of your
     *  own, if they've shared this stock with you at EDIT permission. */
    @PostMapping("/calculate")
    public ResponseEntity<?> calculate(@RequestBody StickerPriceRequest req) {
        Stock stock = stockRepository.findByTicker(req.ticker().toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first"));

        Long effectiveOwner;
        try {
            effectiveOwner = sharingService.resolveEffectiveOwner(
                    CurrentUser.id(), req.ownerId(), stock.getId(), StockShare.Permission.EDIT);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }

        int years = req.yearsToHold() != null && req.yearsToHold() > 0 ? req.yearsToHold() : 10;

        var result = calculationService.calculateStickerPrice(
                req.currentEps(), req.estimatedGrowthPct(), req.estimatedFuturePe(), req.minAcceptableReturnPct(), years);

        StickerPriceCalc calc = new StickerPriceCalc();
        calc.setUserId(effectiveOwner);
        calc.setStockId(stock.getId());
        calc.setCurrentEps(req.currentEps());
        calc.setEstimatedGrowthPct(req.estimatedGrowthPct());
        calc.setEstimatedFuturePe(req.estimatedFuturePe());
        calc.setMinAcceptableReturn(req.minAcceptableReturnPct());
        calc.setYearsToHold(years);
        calc.setFutureEps10y(result.futureEps10y());
        calc.setFuturePrice(result.futurePrice());
        calc.setStickerPrice(result.stickerPrice());
        calc.setMarginOfSafetyPrice(result.marginOfSafetyPrice());
        calc.setCalculatedAt(LocalDateTime.now());

        return ResponseEntity.ok(repository.save(calc));
    }

    @GetMapping("/default-pe")
    public ResponseEntity<BigDecimal> defaultPe(@RequestParam BigDecimal estimatedGrowthPct) {
        return ResponseEntity.ok(calculationService.defaultFuturePe(estimatedGrowthPct));
    }

    /** ownerId (optional): view someone else's saved calculations for this stock, if shared
     *  with you at least at VIEW permission. */
    @GetMapping("/history/{ticker}")
    public ResponseEntity<?> history(@PathVariable String ticker, @RequestParam(required = false) Long ownerId) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        try {
            Long effectiveOwner = sharingService.resolveEffectiveOwner(
                    CurrentUser.id(), ownerId, stock.getId(), StockShare.Permission.VIEW);
            return ResponseEntity.ok(repository.findByUserIdAndStockIdOrderByCalculatedAtDesc(effectiveOwner, stock.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /** Deletes one saved Sticker Price calculation. Deleting a calculation that belongs to
     *  someone else (via a share) requires EDIT permission on that stock's analysis, not just
     *  VIEW — same as editing it in the first place. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var calc = repository.findById(id).orElse(null);
        if (calc == null) return ResponseEntity.notFound().build();

        Long requesterId = CurrentUser.id();
        if (!calc.getUserId().equals(requesterId)) {
            try {
                sharingService.resolveEffectiveOwner(requesterId, calc.getUserId(), calc.getStockId(), StockShare.Permission.EDIT);
            } catch (RuntimeException e) {
                return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
            }
        }
        repository.delete(calc);
        return ResponseEntity.noContent().build();
    }
}
