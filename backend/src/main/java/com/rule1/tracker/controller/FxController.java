package com.rule1.tracker.controller;

import com.rule1.tracker.service.StockApiException;
import com.rule1.tracker.service.StockDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/** Lets the frontend convert portfolio totals between currencies on demand — e.g. "show my
 *  USD + INR holdings combined, in INR" — instead of just warning that currencies are mixed. */
@RestController
@RequestMapping("/api/fx")
public class FxController {

    private final StockDataService stockDataService;

    public FxController(StockDataService stockDataService) {
        this.stockDataService = stockDataService;
    }

    @GetMapping("/rate")
    public ResponseEntity<?> rate(@RequestParam String from, @RequestParam String to) {
        if (from.equalsIgnoreCase(to)) {
            return ResponseEntity.ok(Map.of("rate", BigDecimal.ONE));
        }
        try {
            BigDecimal rate = stockDataService.fetchExchangeRate(from.toUpperCase(), to.toUpperCase());
            return ResponseEntity.ok(Map.of("rate", rate));
        } catch (StockApiException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }
}
