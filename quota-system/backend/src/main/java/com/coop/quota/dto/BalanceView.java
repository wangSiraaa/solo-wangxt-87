package com.coop.quota.dto;

import java.math.BigDecimal;

/** 账户余额视图：全部由账本条目汇总而来，账户表上没有余额字段。 */
public record BalanceView(
        Long accountId,
        String speciesCode, String speciesName,
        String areaCode, String areaName,
        String seasonCode, String seasonName,
        String vesselCode, String vesselName,
        /** 配额口径 = 核拨 + 调入 - 调出 */
        BigDecimal quota,
        /** 在途占用（航次已申报未核实/未结案部分） */
        BigDecimal reserved,
        /** 实捕核销（仅已核实称重） */
        BigDecimal actual,
        /** 可用余额 = quota - reserved - actual = 全部条目之和 */
        BigDecimal available
) {}
