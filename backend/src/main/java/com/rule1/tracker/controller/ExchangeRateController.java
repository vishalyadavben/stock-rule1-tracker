package com.rule1.tracker.controller;

import com.rule1.tracker.service.ExchangeRateService;
import com.rule1.tracker.service.StockApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/exchange-rate")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping
    public ResponseEntity<?> getRate(@RequestParam String from, @RequestParam String to) {
        try {
            return ResponseEntity.ok(Map.of("from", from.toUpperCase(), "to", to.toUpperCase(),
                    "rate", exchangeRateService.getRate(from, to)));
        } catch (StockApiException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }
}
