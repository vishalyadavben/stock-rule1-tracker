package com.rule1.tracker.controller;

import com.rule1.tracker.entity.BigFiveMetric;
import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.repository.BigFiveMetricRepository;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.service.CalculationService;
import com.rule1.tracker.service.StockApiException;
import com.rule1.tracker.service.StockDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockRepository stockRepository;
    private final BigFiveMetricRepository bigFiveMetricRepository;
    private final StockDataService stockDataService;
    private final CalculationService calculationService;

    public StockController(StockRepository stockRepository, BigFiveMetricRepository bigFiveMetricRepository,
                            StockDataService stockDataService, CalculationService calculationService) {
        this.stockRepository = stockRepository;
        this.bigFiveMetricRepository = bigFiveMetricRepository;
        this.stockDataService = stockDataService;
        this.calculationService = calculationService;
    }

    /** Adds a ticker to the master stock list if not present (idempotent).
     *  Optional `currency` query param (defaults to USD) — only applied when the stock is
     *  being created for the very first time. Deliberately NEVER overwrites an existing
     *  stock's currency on a later call: Stock is a single shared record across every user,
     *  so silently "correcting" it here would mislabel it for everyone, not just the caller.
     *  Use the per-holding display-currency feature to view a position converted into a
     *  different currency instead — that never touches this shared record. */
    @PostMapping("/{ticker}")
    public ResponseEntity<Stock> addStock(@PathVariable String ticker, @RequestParam(required = false) String currency) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase()).orElseGet(() -> {
            Stock s = new Stock();
            s.setTicker(ticker.toUpperCase());
            s.setCurrency(currency != null ? currency.toUpperCase() : "USD");
            s.setCreatedAt(LocalDateTime.now());
            return s;
        });
        return ResponseEntity.ok(stockRepository.save(stock));
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<Stock> getStock(@PathVariable String ticker) {
        return stockRepository.findByTicker(ticker.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Lets the user set a stock's price by hand when the API can't fetch it — rate limit hit,
     *  invalid/demo key, or a ticker (e.g. many Indian ones) the API just doesn't cover.
     *  Marks the price as MANUAL so the UI can show it isn't live. */
    @PostMapping("/{ticker}/manual-price")
    public ResponseEntity<Stock> setManualPrice(@PathVariable String ticker, @RequestBody Map<String, BigDecimal> body) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first"));
        BigDecimal price = body.get("price");
        if (price == null) {
            throw new RuntimeException("price is required");
        }
        stock.setLastPrice(price);
        stock.setLastPriceAt(LocalDateTime.now());
        stock.setPriceSource(Stock.PriceSource.MANUAL);
        return ResponseEntity.ok(stockRepository.save(stock));
    }

    /** Pulls latest price from the market data API and updates the stock row.
     *  Returns 502 with a specific error message (instead of silently succeeding with no
     *  price) if Alpha Vantage rejects the request — see StockDataService for why that
     *  distinction matters.
     *  For Indian tickers, Alpha Vantage expects an exchange suffix, e.g. RELIANCE.BSE —
     *  fundamentals (Big Five) generally aren't available for these on the free tier, so use
     *  manual entry for those. If this fails, use /manual-price to set the price by hand
     *  instead of being blocked. */
    @PostMapping("/{ticker}/refresh-price")
    public ResponseEntity<?> refreshPrice(@PathVariable String ticker) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first"));
        try {
            var price = stockDataService.fetchLatestPrice(ticker.toUpperCase());
            stock.setLastPrice(price);
            stock.setLastPriceAt(LocalDateTime.now());
            stock.setPriceSource(Stock.PriceSource.API);
            stockRepository.save(stock);
            return ResponseEntity.ok(stock);
        } catch (StockApiException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    /** Pulls the Big Five fundamentals history from the API and stores/updates them
     *  (upserted by fiscal year — re-refreshing never duplicates or errors). */
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
                    + " — either this ticker isn't covered by the free tier's financial "
                    + "statements (common for non-US tickers), or the daily request limit "
                    + "(25/day) has been hit. Use manual entry instead."));
        }

        for (BigFiveMetric m : fetched) {
            m.setStockId(stock.getId());
            upsertBigFive(m);
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

    /** Manual entry / edit of a single year's Big Five numbers. Upserts by (stock, fiscal
     *  year, MANUAL) — re-submitting the same year edits it in place rather than erroring on
     *  the unique constraint or creating a duplicate. This is what makes an entry "editable." */
    @PostMapping("/{ticker}/big-five/manual")
    public ResponseEntity<BigFiveMetric> saveManualBigFive(@PathVariable String ticker, @RequestBody BigFiveMetric input) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first"));
        input.setStockId(stock.getId());
        input.setSource(BigFiveMetric.Source.MANUAL);
        return ResponseEntity.ok(upsertBigFive(input));
    }

    /** Deletes one year of Big Five data for a given source. Nothing is ever auto-deleted —
     *  this is the only path data disappears through, and the frontend must confirm with the
     *  user before calling it. */
    @DeleteMapping("/{ticker}/big-five/{source}/{fiscalYear}")
    public ResponseEntity<Void> deleteBigFiveYear(@PathVariable String ticker, @PathVariable String source,
                                                   @PathVariable Integer fiscalYear) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        BigFiveMetric.Source src = BigFiveMetric.Source.valueOf(source.toUpperCase());
        bigFiveMetricRepository.findByStockIdAndFiscalYearAndSource(stock.getId(), fiscalYear, src)
                .ifPresent(bigFiveMetricRepository::delete);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{ticker}/big-five")
    public ResponseEntity<List<BigFiveMetric>> getBigFive(@PathVariable String ticker) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        return ResponseEntity.ok(bigFiveMetricRepository.findByStockIdOrderByFiscalYearAsc(stock.getId()));
    }

    /**
     * Per-metric growth rates, computed independently for Sales, EPS, Equity, and Free Cash
     * Flow, plus the latest ROIC — so a gap in one metric never blocks seeing growth for the
     * others. Defaults to 10/5/3/1-year windows; pass `years=10,7,2` (any comma-separated list)
     * to see custom windows instead.
     */
    @GetMapping("/{ticker}/growth-rates")
    public ResponseEntity<Map<String, Object>> growthRates(@PathVariable String ticker,
                                                            @RequestParam(defaultValue = "API") String source,
                                                            @RequestParam(defaultValue = "10,5,3,1") String years) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        BigFiveMetric.Source src = BigFiveMetric.Source.valueOf(source.toUpperCase());
        List<BigFiveMetric> yearly = bigFiveMetricRepository.findByStockIdAndSourceOrderByFiscalYearAsc(stock.getId(), src);

        int[] windows;
        try {
            windows = java.util.Arrays.stream(years.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .mapToInt(Integer::parseInt).toArray();
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
        if (windows.length == 0) windows = new int[]{10, 5, 3, 1};

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sales", calculationService.computeGrowthRates(yearly, "sales", windows));
        result.put("eps", calculationService.computeGrowthRates(yearly, "eps", windows));
        result.put("equity", calculationService.computeGrowthRates(yearly, "equity", windows));
        result.put("freeCashFlow", calculationService.computeGrowthRates(yearly, "freeCashFlow", windows));
        result.put("roic", calculationService.averageRoic(yearly, windows));
        result.put("roicTrend", calculationService.roicTrend(yearly, windows));

        BigDecimal latestRoic = yearly.isEmpty() ? null : yearly.get(yearly.size() - 1).getRoicPct();
        result.put("latestRoicPct", latestRoic);

        return ResponseEntity.ok(result);
    }

    /** Finds an existing row for (stock, fiscal year, source) and updates it in place;
     *  otherwise inserts a new one. Shared by the API refresh and manual entry paths so
     *  neither can violate the unique constraint by re-saving the same year twice. */
    private BigFiveMetric upsertBigFive(BigFiveMetric incoming) {
        var existing = bigFiveMetricRepository.findByStockIdAndFiscalYearAndSource(
                incoming.getStockId(), incoming.getFiscalYear(), incoming.getSource());
        if (existing.isPresent()) {
            BigFiveMetric row = existing.get();
            row.setSales(incoming.getSales());
            row.setEps(incoming.getEps());
            row.setEquity(incoming.getEquity());
            row.setFreeCashFlow(incoming.getFreeCashFlow());
            row.setLongTermDebt(incoming.getLongTermDebt());
            row.setSharesOut(incoming.getSharesOut());
            row.setRoicPct(incoming.getRoicPct());
            return bigFiveMetricRepository.save(row);
        }
        return bigFiveMetricRepository.save(incoming);
    }
}
