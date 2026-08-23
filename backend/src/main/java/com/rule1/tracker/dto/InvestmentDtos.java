package com.rule1.tracker.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InvestmentDtos {
    public record BuyRequest(String ticker, BigDecimal quantity, BigDecimal buyPrice, LocalDateTime buyDate) {}
    public record SellRequest(Long lotId, BigDecimal quantity, BigDecimal sellPrice, LocalDateTime sellDate, String notes) {}

    public record HoldingView(
            Long lotId, String ticker, String companyName, String currency,
            BigDecimal quantity, BigDecimal remainingQuantity,
            BigDecimal buyPrice, LocalDateTime buyDate,
            BigDecimal currentPrice, BigDecimal unrealizedGain, BigDecimal unrealizedGainPct,
            String status
    ) {}

    public record ExitHistoryView(
            Long exitId, Long lotId, String ticker,
            BigDecimal quantitySold, BigDecimal sellPrice, LocalDateTime sellDate,
            BigDecimal buyPrice, LocalDateTime buyDate,
            BigDecimal realizedGain, BigDecimal realizedGainPct, String notes
    ) {}
}
