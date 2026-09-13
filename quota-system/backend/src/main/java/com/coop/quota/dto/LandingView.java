package com.coop.quota.dto;

import com.coop.quota.domain.Landing;

import java.math.BigDecimal;
import java.time.Instant;

public record LandingView(
        Long id, Long voyageId, String portName, String receiptNo,
        BigDecimal estimatedWeight, String status, BigDecimal verifiedWeight, Instant verifiedAt
) {
    public static LandingView of(Landing l) {
        return new LandingView(l.getId(), l.getVoyage().getId(), l.getPortName(), l.getReceiptNo(),
                l.getEstimatedWeight(), l.getStatus().name(), l.getVerifiedWeight(), l.getVerifiedAt());
    }
}
