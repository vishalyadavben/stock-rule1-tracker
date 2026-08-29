package com.rule1.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ipo_applications")
@Data
public class IpoApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "issue_price", nullable = false)
    private BigDecimal issuePrice;

    private BigDecimal quantity;

    @Column(name = "sell_price")
    private BigDecimal sellPrice;

    @Column(name = "sell_date")
    private LocalDateTime sellDate;

    private BigDecimal gmp;

    @Enumerated(EnumType.STRING)
    @Column(name = "gmp_source")
    private GmpSource gmpSource;

    /** Deliberately only the last 4 characters — see migration comment for why. */
    @Column(name = "pan_last4")
    private String panLast4;

    private String notes;

    @Column(name = "is_paper_money")
    private Boolean isPaperMoney = false;

    @Column(name = "application_date")
    private LocalDateTime applicationDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum Status { PENDING, ALLOTTED, NOT_ALLOTTED }
    public enum GmpSource { API, MANUAL }
}
