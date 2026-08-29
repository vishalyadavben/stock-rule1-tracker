package com.rule1.tracker.controller;

import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.entity.StockShare;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.repository.UserRepository;
import com.rule1.tracker.security.CurrentUser;
import com.rule1.tracker.service.SharingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final SharingService sharingService;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    public ShareController(SharingService sharingService, StockRepository stockRepository, UserRepository userRepository) {
        this.sharingService = sharingService;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
    }

    public record ShareRequest(String email, String permission) {}

    @PostMapping("/{ticker}")
    public ResponseEntity<?> share(@PathVariable String ticker, @RequestBody ShareRequest req) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        StockShare.Permission permission;
        try {
            permission = StockShare.Permission.valueOf(req.permission().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "permission must be VIEW or EDIT"));
        }
        if (req.email() == null || req.email().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        var share = sharingService.shareStock(CurrentUser.id(), stock.getId(), req.email(), permission);
        return ResponseEntity.ok(share);
    }

    public record ShareView(Long id, String email, String permission, boolean hasAccount, String createdAt) {}

    @GetMapping("/{ticker}")
    public ResponseEntity<List<ShareView>> myShares(@PathVariable String ticker) {
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));
        List<ShareView> views = sharingService.listMySharesForStock(CurrentUser.id(), stock.getId()).stream()
                .map(s -> new ShareView(s.getId(), s.getSharedWithEmail(), s.getPermission().name(),
                        s.getSharedWithUserId() != null, String.valueOf(s.getCreatedAt())))
                .toList();
        return ResponseEntity.ok(views);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id) {
        try {
            sharingService.revokeShare(CurrentUser.id(), id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    public record SharedWithMeView(Long stockId, String ticker, Long ownerId, String ownerEmail, String permission, String createdAt) {}

    @GetMapping("/shared-with-me")
    public ResponseEntity<List<SharedWithMeView>> sharedWithMe() {
        List<SharedWithMeView> views = sharingService.listSharedWithMe(CurrentUser.id()).stream()
                .map(s -> {
                    Stock stock = stockRepository.findById(s.getStockId()).orElse(null);
                    String ownerEmail = userRepository.findById(s.getOwnerUserId())
                            .map(u -> u.getEmail()).orElse("?");
                    return new SharedWithMeView(
                            s.getStockId(), stock != null ? stock.getTicker() : "?",
                            s.getOwnerUserId(), ownerEmail, s.getPermission().name(), String.valueOf(s.getCreatedAt())
                    );
                })
                .toList();
        return ResponseEntity.ok(views);
    }
}
