package com.rule1.tracker.controller;

import com.rule1.tracker.entity.ChecklistItem;
import com.rule1.tracker.entity.ChecklistResponse;
import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.entity.StockShare;
import com.rule1.tracker.repository.ChecklistItemRepository;
import com.rule1.tracker.repository.ChecklistResponseRepository;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.security.CurrentUser;
import com.rule1.tracker.service.SharingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checklist")
public class ChecklistController {

    private final ChecklistItemRepository itemRepository;
    private final ChecklistResponseRepository responseRepository;
    private final StockRepository stockRepository;
    private final SharingService sharingService;

    public ChecklistController(ChecklistItemRepository itemRepository, ChecklistResponseRepository responseRepository,
                                StockRepository stockRepository, SharingService sharingService) {
        this.itemRepository = itemRepository;
        this.responseRepository = responseRepository;
        this.stockRepository = stockRepository;
        this.sharingService = sharingService;
    }

    /** The master checklist — same for every user, seeded via Flyway. Add more via this table anytime. */
    @GetMapping("/items")
    public ResponseEntity<List<ChecklistItem>> items() {
        return ResponseEntity.ok(itemRepository.findAll());
    }

    /** ownerId (optional): pass another user's id to view THEIR checklist for this stock,
     *  if they've shared it with you (at least VIEW permission). Omit to see your own. */
    @GetMapping("/{ticker}/responses")
    public ResponseEntity<?> responses(@PathVariable String ticker, @RequestParam(required = false) Long ownerId) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        try {
            Long effectiveOwner = sharingService.resolveEffectiveOwner(
                    CurrentUser.id(), ownerId, stock.getId(), StockShare.Permission.VIEW);
            return ResponseEntity.ok(responseRepository.findByUserIdAndStockId(effectiveOwner, stock.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    public record ResponseInput(Long checklistItemId, Boolean isChecked, String freeText, Long ownerId) {}

    /**
     * Upserts a checklist response. Upserts by (user_id, stock_id, checklist_item_id) so
     * re-toggling never hits the unique constraint.
     * ownerId (optional, in the body): editing someone else's shared checklist requires EDIT
     * permission, not just VIEW.
     */
    @PostMapping("/{ticker}/responses")
    public ResponseEntity<?> saveResponse(@PathVariable String ticker, @RequestBody ResponseInput input) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        Long effectiveOwner;
        try {
            effectiveOwner = sharingService.resolveEffectiveOwner(
                    CurrentUser.id(), input.ownerId(), stock.getId(), StockShare.Permission.EDIT);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }

        ChecklistResponse resp = responseRepository
                .findByUserIdAndStockId(effectiveOwner, stock.getId()).stream()
                .filter(r -> r.getChecklistItemId().equals(input.checklistItemId()))
                .findFirst()
                .orElseGet(ChecklistResponse::new);

        resp.setUserId(effectiveOwner);
        resp.setStockId(stock.getId());
        resp.setChecklistItemId(input.checklistItemId());
        resp.setIsChecked(input.isChecked());
        resp.setFreeText(input.freeText());
        resp.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(responseRepository.save(resp));
    }
}
