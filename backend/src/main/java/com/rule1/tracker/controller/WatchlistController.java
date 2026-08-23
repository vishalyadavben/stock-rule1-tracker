package com.rule1.tracker.controller;

import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.entity.WatchlistItem;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.repository.WatchlistItemRepository;
import com.rule1.tracker.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistItemRepository watchlistItemRepository;
    private final StockRepository stockRepository;

    public WatchlistController(WatchlistItemRepository watchlistItemRepository, StockRepository stockRepository) {
        this.watchlistItemRepository = watchlistItemRepository;
        this.stockRepository = stockRepository;
    }

    @GetMapping
    public ResponseEntity<List<WatchlistItem>> list() {
        return ResponseEntity.ok(watchlistItemRepository.findByUserId(CurrentUser.id()));
    }

    @PostMapping("/{ticker}")
    public ResponseEntity<WatchlistItem> add(@PathVariable String ticker, @RequestBody(required = false) String notes) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first via /api/stocks/{ticker}"));
        WatchlistItem item = new WatchlistItem();
        item.setUserId(CurrentUser.id());
        item.setStockId(stock.getId());
        item.setAddedAt(LocalDateTime.now());
        item.setNotes(notes);
        return ResponseEntity.ok(watchlistItemRepository.save(item));
    }
}
