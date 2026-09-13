package com.coop.quota.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 航次。申报时按预计重量占用额度；靠港核实后逐笔转为实扣并释放差额。
 */
@Entity
@Table(name = "voyage")
public class Voyage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String voyageNo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Vessel vessel;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Species species;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private SeaArea seaArea;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Season season;

    /** 申报预计重量（千克） */
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal estimatedWeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoyageStatus status = VoyageStatus.DRAFT;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant closedAt;

    protected Voyage() {}

    public Voyage(String voyageNo, Vessel vessel, Species species, SeaArea seaArea,
                  Season season, BigDecimal estimatedWeight) {
        this.voyageNo = voyageNo;
        this.vessel = vessel;
        this.species = species;
        this.seaArea = seaArea;
        this.season = season;
        this.estimatedWeight = estimatedWeight;
    }

    public Long getId() { return id; }
    public String getVoyageNo() { return voyageNo; }
    public Vessel getVessel() { return vessel; }
    public Species getSpecies() { return species; }
    public SeaArea getSeaArea() { return seaArea; }
    public Season getSeason() { return season; }
    public BigDecimal getEstimatedWeight() { return estimatedWeight; }
    public VoyageStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }

    public void markDeclared() { this.status = VoyageStatus.DECLARED; }
    public void markClosed() { this.status = VoyageStatus.CLOSED; this.closedAt = Instant.now(); }
    public void markCancelled() { this.status = VoyageStatus.CANCELLED; this.closedAt = Instant.now(); }
}
