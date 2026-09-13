package com.coop.quota.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 靠港卸货单。receiptNo（卸货凭证号）全局唯一，保证同一凭证多次回传不重复扣减。
 * 同一航次可在多个港口卸货（多条 Landing）。
 */
@Entity
@Table(name = "landing")
public class Landing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Voyage voyage;

    @Column(nullable = false, length = 80)
    private String portName;

    /** 卸货凭证号：幂等键 */
    @Column(nullable = false, unique = true, length = 60)
    private String receiptNo;

    /** 本票卸货的预计重量（申报分摊） */
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal estimatedWeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LandingStatus status = LandingStatus.PENDING;

    /** 核实后的实际重量；待确认时为空，不计入最终捕捞量 */
    @Column(precision = 19, scale = 3)
    private BigDecimal verifiedWeight;

    private Instant verifiedAt;

    protected Landing() {}

    public Landing(Voyage voyage, String portName, String receiptNo, BigDecimal estimatedWeight) {
        this.voyage = voyage;
        this.portName = portName;
        this.receiptNo = receiptNo;
        this.estimatedWeight = estimatedWeight;
    }

    public Long getId() { return id; }
    public Voyage getVoyage() { return voyage; }
    public String getPortName() { return portName; }
    public String getReceiptNo() { return receiptNo; }
    public BigDecimal getEstimatedWeight() { return estimatedWeight; }
    public LandingStatus getStatus() { return status; }
    public BigDecimal getVerifiedWeight() { return verifiedWeight; }
    public Instant getVerifiedAt() { return verifiedAt; }

    public void markVerified(BigDecimal verifiedWeight) {
        this.status = LandingStatus.VERIFIED;
        this.verifiedWeight = verifiedWeight;
        this.verifiedAt = Instant.now();
    }
}
