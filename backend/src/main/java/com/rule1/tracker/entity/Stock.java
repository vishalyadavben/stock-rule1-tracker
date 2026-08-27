package com.rule1.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocks")
@Data
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String ticker;

    @Column(name = "company_name")
    private String companyName;

    private String sector;
    private String industry;
    private String currency = "USD";

    @Column(name = "last_price")
    private BigDecimal lastPrice;

    @Column(name = "last_price_at")
    private LocalDateTime lastPriceAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_source")
    private PriceSource priceSource;

    public enum PriceSource { API, MANUAL }

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
