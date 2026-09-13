package com.coop.quota.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 配额调拨：在两个同维度（物种+海区+季节相同）账户之间移动额度。
 * 必须保存来源账户、去向账户与生效期间；过账产生一出一进两条账本条目，
 * 而不是修改任何余额字段。
 */
@Entity
@Table(name = "quota_transfer")
public class QuotaTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private QuotaAccount fromAccount;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private QuotaAccount toAccount;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    /** 生效期间：只有落在期间内才允许过账 */
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private LocalDate effectiveTo;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected QuotaTransfer() {}

    public QuotaTransfer(QuotaAccount fromAccount, QuotaAccount toAccount, BigDecimal amount,
                         LocalDate effectiveFrom, LocalDate effectiveTo, String reason) {
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public QuotaAccount getFromAccount() { return fromAccount; }
    public QuotaAccount getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
