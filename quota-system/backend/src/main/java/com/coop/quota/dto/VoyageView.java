package com.coop.quota.dto;

import com.coop.quota.domain.Voyage;

import java.math.BigDecimal;
import java.time.Instant;

public record VoyageView(
        Long id, String voyageNo, String status, BigDecimal estimatedWeight,
        String vesselCode, String speciesCode, String areaCode, String seasonCode,
        Instant createdAt
) {
    public static VoyageView of(Voyage v) {
        return new VoyageView(v.getId(), v.getVoyageNo(), v.getStatus().name(), v.getEstimatedWeight(),
                v.getVessel().getCode(), v.getSpecies().getCode(),
                v.getSeaArea().getCode(), v.getSeason().getCode(), v.getCreatedAt());
    }
}
