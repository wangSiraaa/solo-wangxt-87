package com.coop.quota.domain;

import jakarta.persistence.*;

/**
 * 合作社成员船舶（虚构）。
 */
@Entity
@Table(name = "vessel")
public class Vessel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    protected Vessel() {}

    public Vessel(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
