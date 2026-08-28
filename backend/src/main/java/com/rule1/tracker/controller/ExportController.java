package com.rule1.tracker.controller;

import com.rule1.tracker.entity.*;
import com.rule1.tracker.repository.*;
import com.rule1.tracker.security.CurrentUser;
import com.rule1.tracker.service.CalculationService;
import com.rule1.tracker.service.InvestmentService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Exports the user's investment and research data for download.
 * CSV export is kept as CSV rather than a true .xlsx to avoid pulling in a heavy dependency
 * (Apache POI) for what Excel already reads natively; say the word if you'd rather have real
 * multi-sheet .xlsx output and I'll wire that in instead.
 * The per-stock report is HTML — opens in any browser, and prints to PDF cleanly via the
 * browser's own "Print > Save as PDF" if you want an actual PDF file.
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final InvestmentService investmentService;
    private final StockRepository stockRepository;
    private final BigFiveMetricRepository bigFiveMetricRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistResponseRepository checklistResponseRepository;
    private final StickerPriceCalcRepository stickerPriceCalcRepository;
    private final CalculationService calculationService;

    public ExportController(InvestmentService investmentService, StockRepository stockRepository,
                             BigFiveMetricRepository bigFiveMetricRepository,
                             ChecklistItemRepository checklistItemRepository,
                             ChecklistResponseRepository checklistResponseRepository,
                             StickerPriceCalcRepository stickerPriceCalcRepository,
                             CalculationService calculationService) {
        this.investmentService = investmentService;
        this.stockRepository = stockRepository;
        this.bigFiveMetricRepository = bigFiveMetricRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.checklistResponseRepository = checklistResponseRepository;
        this.stickerPriceCalcRepository = stickerPriceCalcRepository;
        this.calculationService = calculationService;
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

    /** History-only export — mirrors exactly what's shown on the History page (every sell
     *  record, real and paper money, with currency), rather than the fuller combined export
     *  above which also includes active holdings. */
    @GetMapping("/history-csv")
    public void exportHistoryCsv(HttpServletResponse response) throws java.io.IOException {
        Long userId = CurrentUser.id();
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"rule1-tracker-history.csv\"");

        PrintWriter writer = response.getWriter();
        Map<Long, Stock> stockCache = new java.util.HashMap<>();

        List<InvestmentLot> allLots = investmentService.getAllLots(userId);
        Map<Long, InvestmentLot> lotById = new java.util.HashMap<>();
        for (InvestmentLot l : allLots) lotById.put(l.getId(), l);

        writer.println("Ticker,Currency,Type,QuantitySold,BuyPrice,BuyDate,SellPrice,SellDate,RealizedGain,RealizedGainPct,Notes");

        for (InvestmentExit exit : investmentService.getExitHistory(userId)) {
            InvestmentLot lot = lotById.get(exit.getLotId());
            Stock stock = lot == null ? null : stockCache.computeIfAbsent(lot.getStockId(), id -> stockRepository.findById(id).orElse(null));
            String ticker = stock != null ? stock.getTicker() : "?";
            String currency = stock != null ? stock.getCurrency() : "";
            String type = lot != null && Boolean.TRUE.equals(lot.getIsPaperMoney()) ? "Paper" : "Real";
            String notes = exit.getNotes() == null ? "" : exit.getNotes().replace(",", ";");
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                    ticker, currency, type, exit.getQuantitySold(),
                    lot != null ? lot.getBuyPrice() : "", lot != null ? lot.getBuyDate() : "",
                    exit.getSellPrice(), exit.getSellDate(),
                    exit.getRealizedGain(), exit.getRealizedGainPct(), notes);
        }

        writer.flush();
    }

    /** Downloadable single-stock research report: Big Five (both API and manual data),
     *  growth rates, checklist responses, sticker price calc history, and current score. */
    @GetMapping("/report/{ticker}")
    public void exportStockReport(@PathVariable String ticker, HttpServletResponse response) throws java.io.IOException {
        Long userId = CurrentUser.id();
        Stock stock = stockRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        response.setContentType("text/html");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + stock.getTicker() + "-report.html\"");
        PrintWriter w = response.getWriter();

        w.println("<html><head><meta charset='utf-8'><title>" + stock.getTicker() + " — Rule #1 report</title>");
        w.println("<style>body{font-family:Arial,sans-serif;max-width:800px;margin:40px auto;color:#111}"
                + "table{border-collapse:collapse;width:100%;margin-bottom:24px}"
                + "th,td{border:1px solid #ccc;padding:6px 10px;text-align:left;font-size:13px}"
                + "th{background:#f0f0f0}h1{margin-bottom:0}h2{border-bottom:2px solid #333;padding-bottom:4px}"
                + ".muted{color:#666;font-size:13px}</style></head><body>");

        w.println("<h1>" + stock.getTicker() + (stock.getCompanyName() != null ? " — " + stock.getCompanyName() : "") + "</h1>");
        w.println("<p class='muted'>Currency: " + stock.getCurrency() + " · Generated " + java.time.LocalDateTime.now() + "</p>");

        for (BigFiveMetric.Source src : BigFiveMetric.Source.values()) {
            List<BigFiveMetric> yearly = bigFiveMetricRepository.findByStockIdAndSourceOrderByFiscalYearAsc(stock.getId(), src);
            w.println("<h2>Big Five — " + src + " data</h2>");
            if (yearly.isEmpty()) {
                w.println("<p class='muted'>No " + src + " data recorded.</p>");
                continue;
            }
            w.println("<table><tr><th>Year</th><th>Sales</th><th>EPS</th><th>Equity</th>"
                    + "<th>Free Cash Flow</th><th>Long-Term Debt</th><th>ROIC %</th></tr>");
            for (BigFiveMetric m : yearly) {
                w.printf("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>%n",
                        m.getFiscalYear(), nz(m.getSales()), nz(m.getEps()), nz(m.getEquity()),
                        nz(m.getFreeCashFlow()), nz(m.getLongTermDebt()), nz(m.getRoicPct()));
            }
            w.println("</table>");

            w.println("<p><b>10-year growth rates:</b> "
                    + "Sales " + growthStr(yearly, "sales") + " · "
                    + "EPS " + growthStr(yearly, "eps") + " · "
                    + "Equity " + growthStr(yearly, "equity") + " · "
                    + "FCF " + growthStr(yearly, "freeCashFlow") + "</p>");
        }

        w.println("<h2>Four Ms checklist</h2><table><tr><th>Category</th><th>Item</th><th>Checked</th><th>Notes</th></tr>");
        Map<Long, ChecklistResponse> responses = new java.util.HashMap<>();
        for (ChecklistResponse r : checklistResponseRepository.findByUserIdAndStockId(userId, stock.getId())) {
            responses.put(r.getChecklistItemId(), r);
        }
        for (ChecklistItem item : checklistItemRepository.findAll()) {
            ChecklistResponse r = responses.get(item.getId());
            w.printf("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>%n",
                    item.getCategory(), item.getPrompt(),
                    (r != null && Boolean.TRUE.equals(r.getIsChecked())) ? "Yes" : "No",
                    r != null && r.getFreeText() != null ? r.getFreeText().replace("<", "&lt;") : "");
        }
        w.println("</table>");

        w.println("<h2>Sticker Price calculation history</h2>");
        var calcs = stickerPriceCalcRepository.findByUserIdAndStockIdOrderByCalculatedAtDesc(userId, stock.getId());
        if (calcs.isEmpty()) {
            w.println("<p class='muted'>No Sticker Price calculations saved yet.</p>");
        } else {
            w.println("<table><tr><th>Date</th><th>Current EPS</th><th>Growth %</th><th>Future PE</th>"
                    + "<th>Min Return %</th><th>Sticker Price</th><th>Margin-of-Safety Price</th></tr>");
            for (StickerPriceCalc c : calcs) {
                w.printf("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>%n",
                        c.getCalculatedAt(), c.getCurrentEps(), c.getEstimatedGrowthPct(), c.getEstimatedFuturePe(),
                        c.getMinAcceptableReturn(), c.getStickerPrice(), c.getMarginOfSafetyPrice());
            }
            w.println("</table>");
        }

        w.println("</body></html>");
        w.flush();
    }

    private String nz(Object v) { return v == null ? "—" : v.toString(); }

    private String growthStr(List<BigFiveMetric> yearly, String field) {
        var rates = calculationService.computeGrowthRates(yearly, field);
        var r = rates.get("10yr");
        return r == null ? "n/a" : r + "%";
    }
}
