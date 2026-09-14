package com.coop.quota.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 分类修订单：针对某一卸货批次重新划分物种归属。
 * 修订只重新分配扣减量，重量合计不变，不能凭修订增加可捕总量。
 * 原季节已结束也允许修订——调整仍记在原季节账户上，缺口走 Shortfall/Carryover。
 */
@Entity
@Table(name = "classification_revision",
        indexes = @Index(name = "idx_revision_landing", columnList = "landing_id"))
public class ClassificationRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Landing landing;

    @Column(nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RevisionStatus status = RevisionStatus.APPLIED;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant overturnedAt;

    protected ClassificationRevision() {}

    public ClassificationRevision(Landing landing, String reason) {
        this.landing = landing;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Landing getLanding() { return landing; }
    public String getReason() { return reason; }
    public RevisionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getOverturnedAt() { return overturnedAt; }

    public void markOverturned() {
        this.status = RevisionStatus.OVERTURNED;
        this.overturnedAt = Instant.now();
    }
}
