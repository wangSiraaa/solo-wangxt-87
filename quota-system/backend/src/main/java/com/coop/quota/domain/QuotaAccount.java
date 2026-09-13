package com.coop.quota.domain;

import jakarta.persistence.*;

/**
 * 配额账户：以 (物种, 海区, 季节, 船舶) 四元组唯一定位。
 * 物种/海区/季节不匹配的额度落在不同账户，天然无法互相顶替。
 * 账户上没有余额字段——余额由账本条目汇总，杜绝"只改一个余额"。
 */
@Entity
@Table(name = "quota_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_account_dimension",
                columnNames = {"species_id", "sea_area_id", "season_id", "vessel_id"}))
public class QuotaAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Species species;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private SeaArea seaArea;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Season season;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Vessel vessel;

    protected QuotaAccount() {}

    public QuotaAccount(Species species, SeaArea seaArea, Season season, Vessel vessel) {
        this.species = species;
        this.seaArea = seaArea;
        this.season = season;
        this.vessel = vessel;
    }

    public Long getId() { return id; }
    public Species getSpecies() { return species; }
    public SeaArea getSeaArea() { return seaArea; }
    public Season getSeason() { return season; }
    public Vessel getVessel() { return vessel; }
}
