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

    @PostMapping("/{ticker}/responses")
    public ResponseEntity<ChecklistResponse> saveResponse(@PathVariable String ticker, @RequestBody ResponseInput input) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        ChecklistResponse resp = new ChecklistResponse();
        resp.setUserId(CurrentUser.id());
        resp.setStockId(stock.getId());
        resp.setChecklistItemId(input.checklistItemId());
        resp.setIsChecked(input.isChecked());
        resp.setFreeText(input.freeText());
        resp.setUpdatedAt(LocalDateTime.now());
        // Note: relies on the DB unique constraint (user_id, stock_id, checklist_item_id);
        // a production version should find-then-update instead of always inserting.
        return ResponseEntity.ok(responseRepository.save(resp));
    }
}
