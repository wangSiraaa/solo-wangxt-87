package com.coop.quota.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 配额账本条目：不可变、只增不改，是余额的唯一事实来源。
 * amount 带符号（正=增加可用额度，负=减少）。
 */
@Entity
@Table(name = "ledger_entry",
        indexes = {
                @Index(name = "idx_entry_account", columnList = "account_id"),
                @Index(name = "idx_entry_ref", columnList = "refType,refId")
        })
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private QuotaAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LedgerType type;

    /** 带符号重量，单位千克，精度到克 */
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefType refType;

    /** 关联单据主键（航次/卸货/调拨/核拨批次） */
    @Column(nullable = false)
    private Long refId;

    @Column(length = 200)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LedgerEntry() {}

    public LedgerEntry(QuotaAccount account, LedgerType type, BigDecimal amount,
                       RefType refType, Long refId, String note) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.refType = refType;
        this.refId = refId;
        this.note = note;
    }

    public Long getId() { return id; }
    public QuotaAccount getAccount() { return account; }
    public LedgerType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public RefType getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
