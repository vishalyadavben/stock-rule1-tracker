package com.rule1.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sticker_price_calcs")
@Data
public class StickerPriceCalc {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "current_eps")
    private BigDecimal currentEps;

    @Column(name = "estimated_growth_pct")
    private BigDecimal estimatedGrowthPct;

    @Column(name = "estimated_future_pe")
    private BigDecimal estimatedFuturePe;

    @Column(name = "min_acceptable_return")
    private BigDecimal minAcceptableReturn;

    @Column(name = "years_to_hold")
    private Integer yearsToHold;

    @Column(name = "future_eps_10y")
    private BigDecimal futureEps10y;

    @Column(name = "future_price")
    private BigDecimal futurePrice;

    @Column(name = "sticker_price")
    private BigDecimal stickerPrice;

    @Column(name = "margin_of_safety_price")
    private BigDecimal marginOfSafetyPrice;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;
}
