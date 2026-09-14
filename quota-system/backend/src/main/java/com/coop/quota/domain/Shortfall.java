package com.coop.quota.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 待处理缺口：分类修订等原因使账户可用余额为负时生成。
 * 系统只记录缺口，绝不自动撤销他人合法航次；由人工通过结转等方式处置。
 */
@Entity
@Table(name = "shortfall")
public class Shortfall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private QuotaAccount account;

    /** 当前缺口金额（千克），随账户状态刷新 */
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShortfallStatus status = ShortfallStatus.PENDING;

    /** 触发来源（如修订单号），便于追溯 */
    private Long sourceRevisionId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;

    protected Shortfall() {}

    public Shortfall(QuotaAccount account, BigDecimal amount, Long sourceRevisionId, String reason) {
        this.account = account;
        this.amount = amount;
        this.sourceRevisionId = sourceRevisionId;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public QuotaAccount getAccount() { return account; }
    public BigDecimal getAmount() { return amount; }
    public ShortfallStatus getStatus() { return status; }
    public Long getSourceRevisionId() { return sourceRevisionId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }

    public void refresh(BigDecimal amount, String reason) {
        this.amount = amount;
        this.reason = reason;
    }

    public void markResolved() {
        this.status = ShortfallStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }
}
