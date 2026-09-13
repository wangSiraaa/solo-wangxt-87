package com.coop.quota.dto;

import com.coop.quota.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;

/** 账本条目视图：携带单据类型与单据号，前端据此跳转到航次或调拨记录。 */
public record LedgerEntryView(
        Long id,
        String type,
        BigDecimal amount,
        String refType,
        Long refId,
        /** 便于展示的单据编号（航次号/调拨号/凭证号） */
        String refLabel,
        String note,
        Instant createdAt
) {
    public static LedgerEntryView of(LedgerEntry e, String refLabel) {
        return new LedgerEntryView(e.getId(), e.getType().name(), e.getAmount(),
                e.getRefType().name(), e.getRefId(), refLabel, e.getNote(), e.getCreatedAt());
    }
}
