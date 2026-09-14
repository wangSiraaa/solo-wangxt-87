package com.coop.quota.dto;

import com.coop.quota.domain.Carryover;

import java.math.BigDecimal;
import java.time.Instant;

public record CarryoverView(
        Long id, Long fromAccountId, Long toAccountId,
        String fromSeasonCode, String toSeasonCode, String vesselCode, String speciesCode,
        BigDecimal amount, BigDecimal capAmount, Instant createdAt
) {
    public static CarryoverView of(Carryover c) {
        return new CarryoverView(c.getId(), c.getFromAccount().getId(), c.getToAccount().getId(),
                c.getFromAccount().getSeason().getCode(), c.getToAccount().getSeason().getCode(),
                c.getFromAccount().getVessel().getCode(), c.getFromAccount().getSpecies().getCode(),
                c.getAmount(), c.getCapAmount(), c.getCreatedAt());
    }
}
