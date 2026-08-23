package com.rule1.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "investment_exits")
@Data
public class InvestmentExit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Column(name = "quantity_sold")
    private BigDecimal quantitySold;

    @Column(name = "sell_price")
    private BigDecimal sellPrice;

    @Column(name = "sell_date")
    private LocalDateTime sellDate;

    @Column(name = "realized_gain")
    private BigDecimal realizedGain;

    @Column(name = "realized_gain_pct")
    private BigDecimal realizedGainPct;

    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
