package com.rule1.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "investment_lots")
@Data
public class InvestmentLot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    private BigDecimal quantity;

    @Column(name = "buy_price")
    private BigDecimal buyPrice;

    @Column(name = "buy_date")
    private LocalDateTime buyDate;

    @Enumerated(EnumType.STRING)
    private LotStatus status = LotStatus.OPEN;

    @Column(name = "remaining_quantity")
    private BigDecimal remainingQuantity;

    @Column(name = "is_paper_money")
    private Boolean isPaperMoney = false;

    /** Optional display-only currency override — null means "show in the stock's native
     *  currency" (stocks.currency). Never affects buyPrice or any stored financial figure;
     *  conversion happens only at render time via a live exchange rate. */
    @Column(name = "display_currency")
    private String displayCurrency;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum LotStatus { OPEN, PARTIAL, CLOSED }
}
