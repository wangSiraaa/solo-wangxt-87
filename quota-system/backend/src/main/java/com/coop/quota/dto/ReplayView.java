package com.coop.quota.dto;

import java.util.List;

/** 回放结果：截至某时点/某条目的账面余额与当时可见的账本条目。 */
public record ReplayView(BalanceView balance, List<LedgerEntryView> entries) {}
