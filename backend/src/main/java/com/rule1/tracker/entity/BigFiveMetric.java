package com.rule1.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "big_five_metrics")
@Data
public class BigFiveMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Enumerated(EnumType.STRING)
    private Source source = Source.API;

    @Column(name = "roic_pct")
    private BigDecimal roicPct;

    private BigDecimal sales;
    private BigDecimal eps;
    private BigDecimal equity;

    @Column(name = "free_cash_flow")
    private BigDecimal freeCashFlow;

    @Column(name = "long_term_debt")
    private BigDecimal longTermDebt;

    @Column(name = "shares_out")
    private BigDecimal sharesOut;

    public enum Source { API, MANUAL }
}
