package com.rule1.tracker.controller;

import com.rule1.tracker.entity.BigFiveMetric;
import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.entity.StickerPriceCalc;
import com.rule1.tracker.repository.BigFiveMetricRepository;
import com.rule1.tracker.repository.StickerPriceCalcRepository;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.security.CurrentUser;
import com.rule1.tracker.service.CalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sticker-price")
public class StickerPriceController {

    private final CalculationService calculationService;
    private final StickerPriceCalcRepository repository;
    private final StockRepository stockRepository;
    private final BigFiveMetricRepository bigFiveMetricRepository;

    public StickerPriceController(CalculationService calculationService, StickerPriceCalcRepository repository,
                                   StockRepository stockRepository, BigFiveMetricRepository bigFiveMetricRepository) {
        this.calculationService = calculationService;
        this.repository = repository;
        this.stockRepository = stockRepository;
        this.bigFiveMetricRepository = bigFiveMetricRepository;
    }

    /**
     * Enhancement: auto-fill Sticker Price inputs from Big Five history, choosing which data
     * source to trust — API-fetched or manually entered — via the `source` query param.
     * This is the "two options" toggle: call this to pre-fill the form, or skip it entirely
     * and type every field manually (the /calculate endpoint below never requires this).
     */
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
            BigDecimal estimatedFuturePe, BigDecimal minAcceptableReturnPct, Integer yearsToHold
    ) {}

    @PostMapping("/calculate")
    public ResponseEntity<StickerPriceCalc> calculate(@RequestBody StickerPriceRequest req) {
        Stock stock = stockRepository.findByTicker(req.ticker().toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first"));

        int years = req.yearsToHold() != null && req.yearsToHold() > 0 ? req.yearsToHold() : 10;

        var result = calculationService.calculateStickerPrice(
                req.currentEps(), req.estimatedGrowthPct(), req.estimatedFuturePe(), req.minAcceptableReturnPct(), years);

        StickerPriceCalc calc = new StickerPriceCalc();
        calc.setUserId(CurrentUser.id());
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

    /** Suggests a default future PE (2x growth rate) to pre-fill the form — user can still override. */
    @GetMapping("/default-pe")
    public ResponseEntity<BigDecimal> defaultPe(@RequestParam BigDecimal estimatedGrowthPct) {
        return ResponseEntity.ok(calculationService.defaultFuturePe(estimatedGrowthPct));
    }

    @GetMapping("/history/{ticker}")
    public ResponseEntity<List<StickerPriceCalc>> history(@PathVariable String ticker) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        return ResponseEntity.ok(repository.findByUserIdAndStockIdOrderByCalculatedAtDesc(CurrentUser.id(), stock.getId()));
    }

    /** Deletes one saved Sticker Price calculation. Nothing is ever auto-deleted — the
     *  frontend must get an explicit confirmation from the user before calling this. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        var calc = repository.findById(id).orElse(null);
        if (calc == null) return ResponseEntity.notFound().build();
        if (!calc.getUserId().equals(CurrentUser.id())) {
            return ResponseEntity.status(403).body("Not authorized for this calculation");
        }
        repository.delete(calc);
        return ResponseEntity.noContent().build();
    }
}
