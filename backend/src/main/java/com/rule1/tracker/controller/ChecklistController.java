package com.rule1.tracker.controller;

import com.rule1.tracker.entity.ChecklistItem;
import com.rule1.tracker.entity.ChecklistResponse;
import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.repository.ChecklistItemRepository;
import com.rule1.tracker.repository.ChecklistResponseRepository;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/checklist")
public class ChecklistController {

    private final ChecklistItemRepository itemRepository;
    private final ChecklistResponseRepository responseRepository;
    private final StockRepository stockRepository;

    public ChecklistController(ChecklistItemRepository itemRepository, ChecklistResponseRepository responseRepository,
                                StockRepository stockRepository) {
        this.itemRepository = itemRepository;
        this.responseRepository = responseRepository;
        this.stockRepository = stockRepository;
    }

    /** The master checklist — same for every user, seeded from schema.sql. Add more via this table anytime. */
    @GetMapping("/items")
    public ResponseEntity<List<ChecklistItem>> items() {
        return ResponseEntity.ok(itemRepository.findAll());
    }

    @GetMapping("/{ticker}/responses")
    public ResponseEntity<List<ChecklistResponse>> responses(@PathVariable String ticker) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        return ResponseEntity.ok(responseRepository.findByUserIdAndStockId(CurrentUser.id(), stock.getId()));
    }

    public record ResponseInput(Long checklistItemId, Boolean isChecked, String freeText) {}

    /**
     * Upserts a checklist response. The previous version always inserted a new row, which
     * violated the (user_id, stock_id, checklist_item_id) unique constraint on the second
     * toggle of any checkbox — that DB error is exactly what made checkboxes appear "not
     * working." Now we look up any existing response first and update it in place.
     */
    @PostMapping("/{ticker}/responses")
    public ResponseEntity<ChecklistResponse> saveResponse(@PathVariable String ticker, @RequestBody ResponseInput input) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        Long userId = CurrentUser.id();

        ChecklistResponse resp = responseRepository
                .findByUserIdAndStockId(userId, stock.getId()).stream()
                .filter(r -> r.getChecklistItemId().equals(input.checklistItemId()))
                .findFirst()
                .orElseGet(ChecklistResponse::new);

        resp.setUserId(userId);
        resp.setStockId(stock.getId());
        resp.setChecklistItemId(input.checklistItemId());
        resp.setIsChecked(input.isChecked());
        resp.setFreeText(input.freeText());
        resp.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(responseRepository.save(resp));
    }
}
