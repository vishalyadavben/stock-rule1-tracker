package com.rule1.tracker.controller;

import com.rule1.tracker.entity.IpoApplication;
import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.entity.User;
import com.rule1.tracker.entity.WatchlistItem;
import com.rule1.tracker.repository.IpoApplicationRepository;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.repository.UserRepository;
import com.rule1.tracker.repository.WatchlistItemRepository;
import com.rule1.tracker.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tracks IPO applications: allotment status, issue price, optional quantity, an eventual sell,
 * Grey Market Premium (manual entry only — see note on GMP below), notes, a PAN reference (last
 * 4 characters only, deliberately — see the migration comment for why), and a paper-money vs
 * real-money distinction identical to regular holdings (paper deletable freely, real requires
 * password confirmation, and the flag can never change after creation).
 * Every IPO is linked to the same Stock record used everywhere else, and auto-added to the
 * user's watchlist, so it shows up in "My Companies" with full Big Five / checklist / Sticker
 * Price support immediately — no separate feature needed for that.
 */
@RestController
@RequestMapping("/api/ipos")
public class IpoController {

    private final IpoApplicationRepository ipoRepository;
    private final StockRepository stockRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public IpoController(IpoApplicationRepository ipoRepository, StockRepository stockRepository,
                          WatchlistItemRepository watchlistItemRepository, UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.ipoRepository = ipoRepository;
        this.stockRepository = stockRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record IpoRequest(
            String ticker, String companyName, String status,
            BigDecimal issuePrice, BigDecimal quantity,
            BigDecimal sellPrice, LocalDateTime sellDate,
            BigDecimal gmp, String pan, String notes, LocalDateTime applicationDate,
            Boolean isPaperMoney
    ) {}

    public record IpoView(
            Long id, String ticker, String companyName, String currency,
            String status, BigDecimal issuePrice, BigDecimal quantity,
            BigDecimal sellPrice, LocalDateTime sellDate,
            BigDecimal gmp, String gmpSource, String panLast4, String notes, LocalDateTime applicationDate,
            BigDecimal absoluteGain, BigDecimal returnPct, BigDecimal estimatedListingGainPct,
            boolean isPaperMoney
    ) {}

    @GetMapping
    public ResponseEntity<List<IpoView>> list() {
        List<IpoApplication> ipos = ipoRepository.findByUserIdOrderByCreatedAtDesc(CurrentUser.id());
        return ResponseEntity.ok(ipos.stream().map(this::toView).toList());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody IpoRequest req) {
        if (req.ticker() == null || req.ticker().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ticker is required (use a placeholder symbol if not yet listed)"));
        }
        if (req.issuePrice() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "issuePrice is required"));
        }

        String ticker = req.ticker().toUpperCase();
        Stock stock = stockRepository.findByTicker(ticker).orElseGet(() -> {
            Stock s = new Stock();
            s.setTicker(ticker);
            s.setCompanyName(req.companyName());
            s.setCurrency("INR"); // IPOs tracked here are assumed INR (GMP/PAN are India-specific concepts)
            s.setCreatedAt(LocalDateTime.now());
            return stockRepository.save(s);
        });
        if (req.companyName() != null && stock.getCompanyName() == null) {
            stock.setCompanyName(req.companyName());
            stockRepository.save(stock);
        }

        Long userId = CurrentUser.id();
        WatchlistItem watchlistItem = watchlistItemRepository.findByUserIdAndStockId(userId, stock.getId())
                .orElseGet(WatchlistItem::new);
        watchlistItem.setUserId(userId);
        watchlistItem.setStockId(stock.getId());
        if (watchlistItem.getAddedAt() == null) watchlistItem.setAddedAt(LocalDateTime.now());
        watchlistItemRepository.save(watchlistItem);

        IpoApplication ipo = new IpoApplication();
        ipo.setUserId(userId);
        ipo.setStockId(stock.getId());
        ipo.setIsPaperMoney(req.isPaperMoney() != null && req.isPaperMoney()); // only ever set here, at creation
        try {
            applyRequest(ipo, req);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        ipo.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.ok(toView(ipoRepository.save(ipo)));
    }

    /** Edits an application's details. Never touches isPaperMoney — same reasoning as
     *  InvestmentLot: allowing that would let someone mark a real application "paper" purely
     *  to unlock free deletion, defeating the protection entirely. */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody IpoRequest req) {
        IpoApplication ipo = ipoRepository.findById(id).orElse(null);
        if (ipo == null) return ResponseEntity.notFound().build();
        if (!ipo.getUserId().equals(CurrentUser.id())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this IPO application"));
        }
        try {
            applyRequest(ipo, req);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(toView(ipoRepository.save(ipo)));
    }

    /** Deletes a paper-money application. Real-money ones are always rejected here — use
     *  /{id}/delete-confirmed for those instead. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        IpoApplication ipo = ipoRepository.findById(id).orElse(null);
        if (ipo == null) return ResponseEntity.notFound().build();
        if (!ipo.getUserId().equals(CurrentUser.id())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this IPO application"));
        }
        if (ipo.getIsPaperMoney() == null || !ipo.getIsPaperMoney()) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "Only paper-money applications can be deleted without password confirmation. Use the confirmed-delete option for real-money applications."));
        }
        ipoRepository.delete(ipo);
        return ResponseEntity.noContent().build();
    }

    public record ConfirmedDeleteRequest(String password) {}

    /** Deletes a real-money application after verifying the user's password. */
    @PostMapping("/{id}/delete-confirmed")
    public ResponseEntity<?> deleteConfirmed(@PathVariable Long id, @RequestBody ConfirmedDeleteRequest req) {
        IpoApplication ipo = ipoRepository.findById(id).orElse(null);
        if (ipo == null) return ResponseEntity.notFound().build();
        Long userId = CurrentUser.id();
        if (!ipo.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this IPO application"));
        }
        if (req.password() == null || req.password().isBlank()) {
            return ResponseEntity.status(403).body(Map.of("error", "Password confirmation is required."));
        }
        User user = userRepository.findById(userId).orElseThrow();
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(403).body(Map.of("error", "Incorrect password."));
        }
        ipoRepository.delete(ipo);
        return ResponseEntity.noContent().build();
    }

    private void applyRequest(IpoApplication ipo, IpoRequest req) {
        if (req.status() != null) {
            try {
                ipo.setStatus(IpoApplication.Status.valueOf(req.status().toUpperCase()));
            } catch (IllegalArgumentException ignored) { /* leave unchanged on bad value */ }
        }
        if (req.issuePrice() != null) ipo.setIssuePrice(req.issuePrice());
        ipo.setQuantity(req.quantity());

        // A sell can only be recorded once the IPO is actually allotted — you can't sell shares
        // you were never given. Enforced here, not just hidden in the UI, so the rule can't be
        // bypassed by calling the API directly.
        boolean attemptingSell = req.sellPrice() != null || req.sellDate() != null;
        if (attemptingSell && ipo.getStatus() != IpoApplication.Status.ALLOTTED) {
            throw new RuntimeException("Can only record a sell once this IPO's status is set to Allotted.");
        }
        ipo.setSellPrice(req.sellPrice());
        ipo.setSellDate(req.sellDate());

        if (req.gmp() != null) {
            ipo.setGmp(req.gmp());
            ipo.setGmpSource(IpoApplication.GmpSource.MANUAL); // see class comment — no reliable API exists for this
        }
        // Only the last 4 characters of PAN are ever persisted — the full value is discarded
        // immediately, never stored even transiently beyond this request.
        if (req.pan() != null && !req.pan().isBlank()) {
            String trimmed = req.pan().trim();
            ipo.setPanLast4(trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4));
        }
        ipo.setNotes(req.notes());
        ipo.setApplicationDate(req.applicationDate());
    }

    private IpoView toView(IpoApplication ipo) {
        Stock stock = stockRepository.findById(ipo.getStockId()).orElse(null);

        BigDecimal absoluteGain = null, returnPct = null, estimatedListingGainPct = null;
        boolean notAllotted = ipo.getStatus() == IpoApplication.Status.NOT_ALLOTTED;

        if (!notAllotted && ipo.getSellPrice() != null && ipo.getIssuePrice() != null
                && ipo.getIssuePrice().compareTo(BigDecimal.ZERO) > 0) {
            returnPct = ipo.getSellPrice().subtract(ipo.getIssuePrice())
                    .divide(ipo.getIssuePrice(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (ipo.getQuantity() != null) {
                absoluteGain = ipo.getSellPrice().subtract(ipo.getIssuePrice()).multiply(ipo.getQuantity());
            }
        }
        if (!notAllotted && ipo.getSellPrice() == null && ipo.getGmp() != null && ipo.getIssuePrice() != null
                && ipo.getIssuePrice().compareTo(BigDecimal.ZERO) > 0) {
            // Before actually selling, GMP gives a rough estimate of listing-day gain:
            // expected listing price ≈ issue price + GMP.
            estimatedListingGainPct = ipo.getGmp()
                    .divide(ipo.getIssuePrice(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return new IpoView(
                ipo.getId(), stock != null ? stock.getTicker() : "?", stock != null ? stock.getCompanyName() : null,
                stock != null ? stock.getCurrency() : "INR",
                ipo.getStatus().name(), ipo.getIssuePrice(), ipo.getQuantity(),
                ipo.getSellPrice(), ipo.getSellDate(),
                ipo.getGmp(), ipo.getGmpSource() != null ? ipo.getGmpSource().name() : null,
                ipo.getPanLast4(), ipo.getNotes(), ipo.getApplicationDate(),
                absoluteGain, returnPct, estimatedListingGainPct,
                ipo.getIsPaperMoney() != null && ipo.getIsPaperMoney()
        );
    }
}
