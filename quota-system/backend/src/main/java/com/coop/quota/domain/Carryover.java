package com.coop.quota.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 跨季欠额结转：把上一季账户的部分欠额承接到下一季同物种同海区账户。
 * 单独记录承接关系与上限；只转部分金额，上一季负余额不清零。
 */
@Entity
@Table(name = "carryover")
public class Carryover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 旧季（欠额）账户 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private QuotaAccount fromAccount;

    /** 新季（承接）账户：同物种、同海区、同船舶、不同季节 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private QuotaAccount toAccount;

    /** 本次结转金额（不超过欠额，也不超过上限） */
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    /** 本笔承接上限（示例规则），随记录留痕 */
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal capAmount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Carryover() {}

    public Carryover(QuotaAccount fromAccount, QuotaAccount toAccount,
                     BigDecimal amount, BigDecimal capAmount) {
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.capAmount = capAmount;
    }

    public Long getId() { return id; }
    public QuotaAccount getFromAccount() { return fromAccount; }
    public QuotaAccount getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getCapAmount() { return capAmount; }
    public Instant getCreatedAt() { return createdAt; }
}
