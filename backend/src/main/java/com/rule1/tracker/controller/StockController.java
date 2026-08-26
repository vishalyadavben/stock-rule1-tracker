package com.rule1.tracker.controller;

import com.rule1.tracker.entity.BigFiveMetric;
import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.repository.BigFiveMetricRepository;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.service.StockApiException;
import com.rule1.tracker.service.StockDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockRepository stockRepository;
    private final BigFiveMetricRepository bigFiveMetricRepository;
    private final StockDataService stockDataService;

    public StockController(StockRepository stockRepository, BigFiveMetricRepository bigFiveMetricRepository,
                            StockDataService stockDataService) {
        this.stockRepository = stockRepository;
        this.bigFiveMetricRepository = bigFiveMetricRepository;
        this.stockDataService = stockDataService;
    }

    /** Adds a ticker to the master stock list if not present (idempotent). */
    @PostMapping("/{ticker}")
    public ResponseEntity<Stock> addStock(@PathVariable String ticker) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseGet(() -> {
                    Stock s = new Stock();
                    s.setTicker(ticker.toUpperCase());
                    s.setCreatedAt(LocalDateTime.now());
                    return stockRepository.save(s);
                });
        return ResponseEntity.ok(stock);
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<Stock> getStock(@PathVariable String ticker) {
        return stockRepository.findByTicker(ticker.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Pulls latest price from the market data API and updates the stock row.
     *  Returns 502 with a specific error message (instead of silently succeeding with no
     *  price) if Alpha Vantage rejects the request — see StockDataService for why that
     *  distinction matters. */
    @PostMapping("/{ticker}/refresh-price")
    public ResponseEntity<?> refreshPrice(@PathVariable String ticker) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first"));
        try {
            var price = stockDataService.fetchLatestPrice(ticker.toUpperCase());
            stock.setLastPrice(price);
            stock.setLastPriceAt(LocalDateTime.now());
            stockRepository.save(stock);
            return ResponseEntity.ok(stock);
        } catch (StockApiException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    /** Pulls the Big Five fundamentals history from the API and stores/updates them. */
    @PostMapping("/{ticker}/refresh-big-five")
    public ResponseEntity<?> refreshBigFive(@PathVariable String ticker) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first"));

        List<BigFiveMetric> fetched;
        try {
            fetched = stockDataService.fetchBigFiveHistory(ticker.toUpperCase());
        } catch (StockApiException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }

        if (fetched.isEmpty()) {
            return ResponseEntity.status(502).body(Map.of("error",
                    "Alpha Vantage returned no fundamentals data for " + ticker
                    + " — either the free tier doesn't cover this ticker's financial statements, "
                    + "or the daily request limit (25/day) has been hit. Use manual entry instead."));
        }

        for (BigFiveMetric m : fetched) {
            m.setStockId(stock.getId());
            // upsert: if a row for this stock+year+API already exists, this will violate the
            // unique constraint — a production version should do a find-then-update here.
            bigFiveMetricRepository.save(m);
        }
        return ResponseEntity.ok(fetched);
    }

    /** Big Five history filtered to a single source (API or MANUAL) — powers the source
     *  toggle in the sticker price calculator and the chart. */
    @GetMapping("/{ticker}/big-five/{source}")
    public ResponseEntity<List<BigFiveMetric>> getBigFiveBySource(@PathVariable String ticker, @PathVariable String source) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        BigFiveMetric.Source src = BigFiveMetric.Source.valueOf(source.toUpperCase());
        return ResponseEntity.ok(bigFiveMetricRepository.findByStockIdAndSourceOrderByFiscalYearAsc(stock.getId(), src));
    }

    /** Manual entry / override of a single year's Big Five numbers. */
    @PostMapping("/{ticker}/big-five/manual")
    public ResponseEntity<BigFiveMetric> saveManualBigFive(@PathVariable String ticker, @RequestBody BigFiveMetric input) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first"));
        input.setStockId(stock.getId());
        input.setSource(BigFiveMetric.Source.MANUAL);
        return ResponseEntity.ok(bigFiveMetricRepository.save(input));
    }

    @GetMapping("/{ticker}/big-five")
    public ResponseEntity<List<BigFiveMetric>> getBigFive(@PathVariable String ticker) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        return ResponseEntity.ok(bigFiveMetricRepository.findByStockIdOrderByFiscalYearAsc(stock.getId()));
    }
}
