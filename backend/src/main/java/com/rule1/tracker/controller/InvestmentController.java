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
        for (Long stockId : stockIds) {
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
                    lot.getQuantity(), lot.getRemainingQuantity(), lot.getBuyPrice(), lot.getBuyDate(),
                    currentPrice, unrealizedGain, unrealizedGainPct, lot.getStatus().name()
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
                    exit.getQuantitySold(), exit.getSellPrice(), exit.getSellDate(),
                    lot != null ? lot.getBuyPrice() : null, lot != null ? lot.getBuyDate() : null,
                    exit.getRealizedGain(), exit.getRealizedGainPct(), exit.getNotes()
            );
        }).toList();

        return ResponseEntity.ok(views);
    }
}
