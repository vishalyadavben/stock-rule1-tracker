package com.rule1.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches live currency conversion rates (e.g. USD -> INR) from Frankfurter (a free, no-API-key
 * exchange rate service backed by the European Central Bank). Used to convert mixed-currency
 * portfolio totals into a single display currency the user picks — not to convert individual
 * stock prices, which always stay in their native currency.
 * Rates are cached in memory for 1 hour since they don't need to be more real-time than that
 * for portfolio-total purposes, and it keeps this well within any free-tier rate limit.
 */
@Service
public class ExchangeRateService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, CachedRate> cache = new ConcurrentHashMap<>();
    private static final Duration TTL = Duration.ofHours(1);

    public BigDecimal getRate(String from, String to) {
        if (from.equalsIgnoreCase(to)) return BigDecimal.ONE;
        String key = from.toUpperCase() + "_" + to.toUpperCase();

        CachedRate cached = cache.get(key);
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(TTL) < 0) {
            return cached.rate();
        }

        String url = String.format("https://api.frankfurter.app/latest?from=%s&to=%s", from.toUpperCase(), to.toUpperCase());
        JsonNode root;
        try {
            root = restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            throw new StockApiException("Could not fetch exchange rate " + from + "->" + to + ": " + e.getMessage());
        }
        if (root == null || !root.has("rates") || !root.get("rates").has(to.toUpperCase())) {
            throw new StockApiException("Exchange rate service returned no rate for " + from + "->" + to);
        }
        BigDecimal rate = new BigDecimal(root.get("rates").get(to.toUpperCase()).asText())
                .setScale(6, RoundingMode.HALF_UP);
        cache.put(key, new CachedRate(rate, Instant.now()));
        return rate;
    }

    /**
     * The FX rate as of a specific historical date — used to lock in the rate at the time of a
     * purchase, so cost basis converts at the rate that applied then, not today's rate. Not
     * cached (each date is a one-off historical lookup, not something that changes on refresh).
     * Frankfurter only publishes rates for days the ECB actually published a reference rate
     * (weekdays) — if the exact date has none, it returns the most recent prior rate, which is
     * a reasonable approximation for a same-week purchase.
     */
    public BigDecimal getHistoricalRate(String from, String to, java.time.LocalDate date) {
        if (from.equalsIgnoreCase(to)) return BigDecimal.ONE;
        String url = String.format("https://api.frankfurter.app/%s?from=%s&to=%s",
                date, from.toUpperCase(), to.toUpperCase());
        JsonNode root;
        try {
            root = restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            throw new StockApiException("Could not fetch historical exchange rate " + from + "->" + to + " for " + date + ": " + e.getMessage());
        }
        if (root == null || !root.has("rates") || !root.get("rates").has(to.toUpperCase())) {
            throw new StockApiException("No historical rate available for " + from + "->" + to + " on " + date);
        }
        return new BigDecimal(root.get("rates").get(to.toUpperCase()).asText()).setScale(6, RoundingMode.HALF_UP);
    }

    private record CachedRate(BigDecimal rate, Instant fetchedAt) {}
}
