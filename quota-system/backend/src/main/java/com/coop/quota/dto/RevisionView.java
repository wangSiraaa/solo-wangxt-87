package com.coop.quota.dto;

import com.coop.quota.domain.ClassificationRevision;
import com.coop.quota.domain.LandingComponent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RevisionView(
        Long id, Long landingId, String receiptNo, String reason, String status,
        List<Line> components, Instant createdAt, Instant overturnedAt
) {
    public record Line(String speciesCode, String speciesName, BigDecimal weight) {}

    public static RevisionView of(ClassificationRevision r, List<LandingComponent> lines) {
        return new RevisionView(r.getId(), r.getLanding().getId(), r.getLanding().getReceiptNo(),
                r.getReason(), r.getStatus().name(),
                lines.stream().map(c -> new Line(c.getSpecies().getCode(),
                        c.getSpecies().getName(), c.getWeight())).toList(),
                r.getCreatedAt(), r.getOverturnedAt());
    }
}
