package com.coop.quota.domain;

import jakarta.persistence.*;

/**
 * 虚构海区。
 */
@Entity
@Table(name = "sea_area")
public class SeaArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    protected SeaArea() {}

    public SeaArea(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
