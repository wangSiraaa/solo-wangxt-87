package com.coop.quota.dto;

import com.coop.quota.domain.QuotaTransfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransferView(
        Long id, Long fromAccountId, Long toAccountId,
        String fromVesselCode, String toVesselCode,
        BigDecimal amount, LocalDate effectiveFrom, LocalDate effectiveTo,
        String reason, Instant createdAt
) {
    public static TransferView of(QuotaTransfer t) {
        return new TransferView(t.getId(), t.getFromAccount().getId(), t.getToAccount().getId(),
                t.getFromAccount().getVessel().getCode(), t.getToAccount().getVessel().getCode(),
                t.getAmount(), t.getEffectiveFrom(), t.getEffectiveTo(), t.getReason(), t.getCreatedAt());
    }
}
