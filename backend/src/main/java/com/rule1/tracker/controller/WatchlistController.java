package com.rule1.tracker.controller;

import com.rule1.tracker.entity.BigFiveMetric;
import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.entity.WatchlistItem;
import com.rule1.tracker.repository.BigFiveMetricRepository;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.repository.WatchlistItemRepository;
import com.rule1.tracker.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** "My Companies" — every stock a user has added (via search, buy, or explicit watchlist add),
 *  so nothing gets lost just because it isn't an active holding. This is what makes stocks
 *  findable again after navigating away — Big Five and checklist data was never actually
 *  deleted, it just had no list pointing back to it. */
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistItemRepository watchlistItemRepository;
    private final StockRepository stockRepository;
    private final BigFiveMetricRepository bigFiveMetricRepository;

    public WatchlistController(WatchlistItemRepository watchlistItemRepository, StockRepository stockRepository,
                                BigFiveMetricRepository bigFiveMetricRepository) {
        this.watchlistItemRepository = watchlistItemRepository;
        this.stockRepository = stockRepository;
        this.bigFiveMetricRepository = bigFiveMetricRepository;
    }

    public record WatchlistView(
            Long id, String ticker, String companyName, String currency,
            java.math.BigDecimal lastPrice, String priceSource, LocalDateTime addedAt,
            boolean hasApiBigFive, boolean hasManualBigFive
    ) {}

    @GetMapping
    public ResponseEntity<List<WatchlistView>> list() {
        List<WatchlistItem> items = watchlistItemRepository.findByUserId(CurrentUser.id());
        List<WatchlistView> views = items.stream().map(item -> {
            Stock stock = stockRepository.findById(item.getStockId()).orElse(null);
            if (stock == null) return null;
            boolean hasApi = !bigFiveMetricRepository
                    .findByStockIdAndSourceOrderByFiscalYearAsc(stock.getId(), BigFiveMetric.Source.API).isEmpty();
            boolean hasManual = !bigFiveMetricRepository
                    .findByStockIdAndSourceOrderByFiscalYearAsc(stock.getId(), BigFiveMetric.Source.MANUAL).isEmpty();
            return new WatchlistView(
                    item.getId(), stock.getTicker(), stock.getCompanyName(), stock.getCurrency(),
                    stock.getLastPrice(), stock.getPriceSource() != null ? stock.getPriceSource().name() : null,
                    item.getAddedAt(), hasApi, hasManual
            );
        }).filter(java.util.Objects::nonNull).toList();
        return ResponseEntity.ok(views);
    }

    /** Idempotent — safe to call every time a stock is viewed or bought, won't duplicate. */
    @PostMapping("/{ticker}")
    public ResponseEntity<WatchlistItem> add(@PathVariable String ticker, @RequestBody(required = false) String notes) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first via /api/stocks/{ticker}"));
        Long userId = CurrentUser.id();

        WatchlistItem item = watchlistItemRepository.findByUserIdAndStockId(userId, stock.getId())
                .orElseGet(WatchlistItem::new);
        item.setUserId(userId);
        item.setStockId(stock.getId());
        if (item.getAddedAt() == null) item.setAddedAt(LocalDateTime.now());
        if (notes != null) item.setNotes(notes);
        return ResponseEntity.ok(watchlistItemRepository.save(item));
    }
}
