package com.coop.quota.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 卸货批次的物种分类行。靠港时混合渔获先按估计比例拆成多行（revision 为空），
 * 后续检测改变物种归属时，新分类行挂在对应 ClassificationRevision 上。
 * 任一批次全部分类行重量合计恒等于该批次核实重量。
 */
@Entity
@Table(name = "landing_component",
        indexes = @Index(name = "idx_component_landing", columnList = "landing_id"))
public class LandingComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Landing landing;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Species species;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal weight;

    /** 为空表示靠港时的初始分类；否则属于某次分类修订 */
    @ManyToOne(fetch = FetchType.LAZY)
    private ClassificationRevision revision;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LandingComponent() {}

    public LandingComponent(Landing landing, Species species, BigDecimal weight,
                            ClassificationRevision revision) {
        this.landing = landing;
        this.species = species;
        this.weight = weight;
        this.revision = revision;
    }

    public Long getId() { return id; }
    public Landing getLanding() { return landing; }
    public Species getSpecies() { return species; }
    public BigDecimal getWeight() { return weight; }
    public ClassificationRevision getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
}
