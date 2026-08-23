package com.rule1.tracker.controller;

import com.rule1.tracker.entity.InvestmentExit;
import com.rule1.tracker.entity.InvestmentLot;
import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.security.CurrentUser;
import com.rule1.tracker.service.InvestmentService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Exports the user's full investment data (active holdings + full exit history) as CSV —
 * opens directly in Excel/Numbers/Google Sheets. Kept as CSV rather than a true .xlsx to avoid
 * pulling in a heavy dependency (Apache POI) for what Excel already reads natively; say the
 * word if you'd rather have real multi-sheet .xlsx output and I'll wire that in instead.
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final InvestmentService investmentService;
    private final StockRepository stockRepository;

    public ExportController(InvestmentService investmentService, StockRepository stockRepository) {
        this.investmentService = investmentService;
        this.stockRepository = stockRepository;
    }

    @GetMapping("/csv")
    public void exportCsv(HttpServletResponse response) throws java.io.IOException {
        Long userId = CurrentUser.id();
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"rule1-tracker-export.csv\"");

        PrintWriter writer = response.getWriter();

        Map<Long, Stock> stockCache = new java.util.HashMap<>();

        writer.println("RecordType,Ticker,Quantity,BuyPrice,BuyDate,SellPrice,SellDate,CurrentPrice,Gain,GainPct,Status,Notes");

        // Active / partial holdings
        for (InvestmentLot lot : investmentService.getActiveHoldings(userId)) {
            Stock stock = stockCache.computeIfAbsent(lot.getStockId(), id -> stockRepository.findById(id).orElse(null));
            String ticker = stock != null ? stock.getTicker() : "?";
            var currentPrice = stock != null ? stock.getLastPrice() : null;
            String gain = "", gainPct = "";
            if (currentPrice != null) {
                var g = currentPrice.subtract(lot.getBuyPrice()).multiply(lot.getRemainingQuantity());
                var gp = currentPrice.subtract(lot.getBuyPrice())
                        .divide(lot.getBuyPrice(), 6, java.math.RoundingMode.HALF_UP)
                        .multiply(java.math.BigDecimal.valueOf(100));
                gain = g.toPlainString();
                gainPct = gp.toPlainString();
            }
            writer.printf("HOLDING,%s,%s,%s,%s,,,%s,%s,%s,%s,%n",
                    ticker, lot.getRemainingQuantity(), lot.getBuyPrice(), lot.getBuyDate(),
                    currentPrice != null ? currentPrice.toPlainString() : "",
                    gain, gainPct, lot.getStatus());
        }

        // Full exit history — every sell, preserved even after full exit
        List<InvestmentLot> allLots = investmentService.getAllLots(userId);
        Map<Long, InvestmentLot> lotById = new java.util.HashMap<>();
        for (InvestmentLot l : allLots) lotById.put(l.getId(), l);

        for (InvestmentExit exit : investmentService.getExitHistory(userId)) {
            InvestmentLot lot = lotById.get(exit.getLotId());
            Stock stock = lot == null ? null : stockCache.computeIfAbsent(lot.getStockId(), id -> stockRepository.findById(id).orElse(null));
            String ticker = stock != null ? stock.getTicker() : "?";
            String notes = exit.getNotes() == null ? "" : exit.getNotes().replace(",", ";");
            writer.printf("EXIT,%s,%s,%s,%s,%s,%s,,%s,%s,CLOSED,%s%n",
                    ticker, exit.getQuantitySold(),
                    lot != null ? lot.getBuyPrice() : "", lot != null ? lot.getBuyDate() : "",
                    exit.getSellPrice(), exit.getSellDate(),
                    exit.getRealizedGain(), exit.getRealizedGainPct(), notes);
        }

        writer.flush();
    }
}
