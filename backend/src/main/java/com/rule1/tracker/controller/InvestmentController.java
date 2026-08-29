package com.rule1.tracker.controller;

import com.rule1.tracker.dto.InvestmentDtos.*;
import com.rule1.tracker.entity.InvestmentExit;
import com.rule1.tracker.entity.InvestmentLot;
import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.repository.InvestmentLotRepository;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.security.CurrentUser;
import com.rule1.tracker.service.InvestmentService;
import com.rule1.tracker.service.StockApiException;
import com.rule1.tracker.service.StockDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    private final InvestmentService investmentService;
    private final StockRepository stockRepository;
    private final InvestmentLotRepository lotRepository;
    private final StockDataService stockDataService;

    public InvestmentController(InvestmentService investmentService, StockRepository stockRepository,
                                 InvestmentLotRepository lotRepository, StockDataService stockDataService) {
        this.investmentService = investmentService;
        this.stockRepository = stockRepository;
        this.lotRepository = lotRepository;
        this.stockDataService = stockDataService;
    }

    /** Refreshes last_price for every stock the user currently holds, in one click.
     *  Returns per-ticker success/failure so a single rate-limited ticker doesn't hide
     *  the fact that others succeeded. */
    @PostMapping("/refresh-prices")
    public ResponseEntity<Map<String, String>> refreshPrices() {
        Long userId = CurrentUser.id();
        List<InvestmentLot> lots = investmentService.getActiveHoldings(userId);
        List<Long> stockIds = lots.stream().map(InvestmentLot::getStockId).distinct().toList();

        Map<String, String> results = new java.util.LinkedHashMap<>();
        boolean first = true;
        for (Long stockId : stockIds) {
            if (!first) {
                try { Thread.sleep(1200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            first = false;
            Stock stock = stockRepository.findById(stockId).orElse(null);
            if (stock == null) continue;
            try {
                var price = stockDataService.fetchLatestPrice(stock.getTicker());
                stock.setLastPrice(price);
                stock.setLastPriceAt(LocalDateTime.now());
                stockRepository.save(stock);
                results.put(stock.getTicker(), "ok");
            } catch (StockApiException e) {
                results.put(stock.getTicker(), "error: " + e.getMessage());
            }
        }
        return ResponseEntity.ok(results);
    }

    @PostMapping("/buy")
    public ResponseEntity<InvestmentLot> buy(@RequestBody BuyRequest req) {
        return ResponseEntity.ok(investmentService.buy(CurrentUser.id(), req));
    }

    @PostMapping("/sell")
    public ResponseEntity<InvestmentExit> sell(@RequestBody SellRequest req) {
        return ResponseEntity.ok(investmentService.sell(CurrentUser.id(), req));
    }

    /** Active holdings dashboard — includes real-time unrealized gain using each stock's last fetched price. */
    @GetMapping("/holdings")
    public ResponseEntity<List<HoldingView>> holdings() {
        Long userId = CurrentUser.id();
        List<InvestmentLot> lots = investmentService.getActiveHoldings(userId);

        Map<Long, Stock> stockCache = new java.util.HashMap<>();
        List<HoldingView> views = lots.stream().map(lot -> {
            Stock stock = stockCache.computeIfAbsent(lot.getStockId(),
                    id -> stockRepository.findById(id).orElse(null));
            BigDecimal currentPrice = stock != null ? stock.getLastPrice() : null;
            BigDecimal unrealizedGain = null, unrealizedGainPct = null;
            if (currentPrice != null) {
                unrealizedGain = currentPrice.subtract(lot.getBuyPrice()).multiply(lot.getRemainingQuantity());
                unrealizedGainPct = currentPrice.subtract(lot.getBuyPrice())
                        .divide(lot.getBuyPrice(), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
            return new HoldingView(
                    lot.getId(), stock != null ? stock.getTicker() : "?",
                    stock != null ? stock.getCompanyName() : null,
                    stock != null ? stock.getCurrency() : "USD",
                    lot.getDisplayCurrency(),
                    lot.getQuantity(), lot.getRemainingQuantity(), lot.getBuyPrice(), lot.getBuyDate(),
                    currentPrice, stock != null && stock.getPriceSource() != null ? stock.getPriceSource().name() : null,
                    unrealizedGain, unrealizedGainPct, lot.getStatus().name(),
                    lot.getIsPaperMoney() != null && lot.getIsPaperMoney(),
                    lot.getBuyFxRate(), lot.getBuyFxRateToCurrency()
            );
        }).toList();

        return ResponseEntity.ok(views);
    }

    /** Full exit history — preserved forever, even after a position is fully closed. */
    @GetMapping("/history")
    public ResponseEntity<List<ExitHistoryView>> history() {
        Long userId = CurrentUser.id();
        List<InvestmentExit> exits = investmentService.getExitHistory(userId);
        List<InvestmentLot> allLots = investmentService.getAllLots(userId);
        Map<Long, InvestmentLot> lotById = allLots.stream()
                .collect(java.util.stream.Collectors.toMap(InvestmentLot::getId, l -> l));
        Map<Long, Stock> stockCache = new java.util.HashMap<>();

        List<ExitHistoryView> views = exits.stream().map(exit -> {
            InvestmentLot lot = lotById.get(exit.getLotId());
            Stock stock = lot == null ? null : stockCache.computeIfAbsent(lot.getStockId(),
                    id -> stockRepository.findById(id).orElse(null));
            return new ExitHistoryView(
                    exit.getId(), exit.getLotId(), stock != null ? stock.getTicker() : "?",
                    stock != null ? stock.getCurrency() : "USD",
                    exit.getQuantitySold(), exit.getSellPrice(), exit.getSellDate(),
                    lot != null ? lot.getBuyPrice() : null, lot != null ? lot.getBuyDate() : null,
                    exit.getRealizedGain(), exit.getRealizedGainPct(), exit.getNotes(),
                    lot != null && lot.getIsPaperMoney() != null && lot.getIsPaperMoney()
            );
        }).toList();

        return ResponseEntity.ok(views);
    }

    /** Deletes a paper-money lot (and its exit history, via DB cascade). Real-money lots are
     *  always rejected here — enforced in the service layer, not just the UI. Use
     *  /lots/{lotId}/delete-confirmed for real-money positions instead. */
    @DeleteMapping("/lots/{lotId}")
    public ResponseEntity<?> deleteLot(@PathVariable Long lotId) {
        try {
            investmentService.deleteLot(CurrentUser.id(), lotId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    public record EditLotRequest(BigDecimal buyPrice, LocalDateTime buyDate, String password) {}

    /** Edits a lot's buy price / buy date. Works for both paper and real money — password is
     *  only actually checked when the lot is real money (see InvestmentService for why). */
    @PutMapping("/lots/{lotId}")
    public ResponseEntity<?> editLot(@PathVariable Long lotId, @RequestBody EditLotRequest req) {
        try {
            return ResponseEntity.ok(investmentService.editLot(
                    CurrentUser.id(), lotId, req.buyPrice(), req.buyDate(), req.password()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    public record DisplayCurrencyRequest(String displayCurrency) {}

    /** Sets (or clears, if displayCurrency is null/blank) a purely cosmetic display-currency
     *  override for one holding. Deliberately does NOT require a password even for real-money
     *  lots — this never touches buyPrice, quantity, or any financial figure, only which
     *  currency the frontend converts to for display. Real conversion math happens client-side
     *  using the live /api/exchange-rate endpoint. */
    @PutMapping("/lots/{lotId}/display-currency")
    public ResponseEntity<?> setDisplayCurrency(@PathVariable Long lotId, @RequestBody DisplayCurrencyRequest req) {
        InvestmentLot lot = lotRepository.findById(lotId).orElse(null);
        if (lot == null) return ResponseEntity.notFound().build();
        if (!lot.getUserId().equals(CurrentUser.id())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this lot"));
        }
        String value = req.displayCurrency();
        lot.setDisplayCurrency(value == null || value.isBlank() ? null : value.toUpperCase());
        return ResponseEntity.ok(lotRepository.save(lot));
    }

    public record ConfirmedDeleteRequest(String password) {}

    /** Deletes ANY lot (paper or real) after verifying the user's password — this is the path
     *  that makes real-money positions deletable at all: not freely, only with an explicit
     *  re-confirmation of identity first. */
    @PostMapping("/lots/{lotId}/delete-confirmed")
    public ResponseEntity<?> deleteLotConfirmed(@PathVariable Long lotId, @RequestBody ConfirmedDeleteRequest req) {
        try {
            investmentService.deleteLotWithPasswordConfirmation(CurrentUser.id(), lotId, req.password());
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    public record EditExitRequest(BigDecimal sellPrice, LocalDateTime sellDate, String notes, String password) {}

    /** Edits a sell record — password required and checked only if the underlying lot is
     *  real money. */
    @PutMapping("/exits/{exitId}")
    public ResponseEntity<?> editExit(@PathVariable Long exitId, @RequestBody EditExitRequest req) {
        try {
            return ResponseEntity.ok(investmentService.editExit(
                    CurrentUser.id(), exitId, req.sellPrice(), req.sellDate(), req.notes(), req.password()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /** Deletes a sell record and restores the sold quantity back onto the lot. Password
     *  required and checked only if the underlying lot is real money. */
    @PostMapping("/exits/{exitId}/delete-confirmed")
    public ResponseEntity<?> deleteExit(@PathVariable Long exitId, @RequestBody ConfirmedDeleteRequest req) {
        try {
            investmentService.deleteExit(CurrentUser.id(), exitId, req.password());
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}
