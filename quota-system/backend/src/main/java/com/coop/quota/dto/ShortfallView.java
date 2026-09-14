package com.coop.quota.dto;

import com.coop.quota.domain.Shortfall;

import java.math.BigDecimal;
import java.time.Instant;

public record ShortfallView(
        Long id, Long accountId, String vesselCode, String speciesCode,
        String areaCode, String seasonCode,
        BigDecimal amount, String status, Long sourceRevisionId, String reason,
        Instant createdAt, Instant resolvedAt
) {
    public static ShortfallView of(Shortfall s) {
        return new ShortfallView(s.getId(), s.getAccount().getId(),
                s.getAccount().getVessel().getCode(), s.getAccount().getSpecies().getCode(),
                s.getAccount().getSeaArea().getCode(), s.getAccount().getSeason().getCode(),
                s.getAmount(), s.getStatus().name(), s.getSourceRevisionId(), s.getReason(),
                s.getCreatedAt(), s.getResolvedAt());
    }
}
