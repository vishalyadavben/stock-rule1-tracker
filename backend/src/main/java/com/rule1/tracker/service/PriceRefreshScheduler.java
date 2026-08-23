package com.rule1.tracker.service;

import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.repository.StockRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Periodically refreshes last_price for every stock in the system so the dashboard shows
 * "real-time" (well — refreshed on a schedule) returns without every page load hitting the
 * external API directly (which would blow through free-tier rate limits fast).
 *
 * Free Alpha Vantage tier = 25 req/day, so keep this interval generous, or fetch only for
 * tickers that appear in an active holding/watchlist (left as a TODO — see README blind spots).
 */
@Component
public class PriceRefreshScheduler {

    private final StockRepository stockRepository;
    private final StockDataService stockDataService;

    public PriceRefreshScheduler(StockRepository stockRepository, StockDataService stockDataService) {
        this.stockRepository = stockRepository;
        this.stockDataService = stockDataService;
    }

    // every 15 minutes; tune via cron based on your API plan's rate limit
    @Scheduled(cron = "0 */15 * * * *")
    public void refreshAllPrices() {
        for (Stock stock : stockRepository.findAll()) {
            try {
                BigDecimal price = stockDataService.fetchLatestPrice(stock.getTicker());
                if (price != null) {
                    stock.setLastPrice(price);
                    stock.setLastPriceAt(LocalDateTime.now());
                    stockRepository.save(stock);
                }
            } catch (Exception e) {
                // don't let one bad ticker kill the whole refresh cycle
                System.err.println("Price refresh failed for " + stock.getTicker() + ": " + e.getMessage());
            }
        }
    }
}
