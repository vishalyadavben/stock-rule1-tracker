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

    /** FX rate from the stock's native currency to the OTHER supported currency, locked in as
     *  of the buy date — used to convert cost basis correctly when viewing this holding in a
     *  different currency (current value uses the live rate instead; see Dashboard). Null if
     *  the historical rate couldn't be fetched at buy time (falls back to live rate for both
     *  sides in that case). */
    @Column(name = "buy_fx_rate")
    private BigDecimal buyFxRate;

    @Column(name = "buy_fx_rate_to_currency")
    private String buyFxRateToCurrency;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum LotStatus { OPEN, PARTIAL, CLOSED }
}
