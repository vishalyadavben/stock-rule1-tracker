package com.rule1.tracker.controller;

import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.entity.StickerPriceCalc;
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

    public StickerPriceController(CalculationService calculationService, StickerPriceCalcRepository repository,
                                   StockRepository stockRepository) {
        this.calculationService = calculationService;
        this.repository = repository;
        this.stockRepository = stockRepository;
    }

    public record StickerPriceRequest(
            String ticker, BigDecimal currentEps, BigDecimal estimatedGrowthPct,
            BigDecimal estimatedFuturePe, BigDecimal minAcceptableReturnPct
    ) {}

    @PostMapping("/calculate")
    public ResponseEntity<StickerPriceCalc> calculate(@RequestBody StickerPriceRequest req) {
        Stock stock = stockRepository.findByTicker(req.ticker().toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first"));

        var result = calculationService.calculateStickerPrice(
                req.currentEps(), req.estimatedGrowthPct(), req.estimatedFuturePe(), req.minAcceptableReturnPct());

        StickerPriceCalc calc = new StickerPriceCalc();
        calc.setUserId(CurrentUser.id());
        calc.setStockId(stock.getId());
        calc.setCurrentEps(req.currentEps());
        calc.setEstimatedGrowthPct(req.estimatedGrowthPct());
        calc.setEstimatedFuturePe(req.estimatedFuturePe());
        calc.setMinAcceptableReturn(req.minAcceptableReturnPct());
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
}
