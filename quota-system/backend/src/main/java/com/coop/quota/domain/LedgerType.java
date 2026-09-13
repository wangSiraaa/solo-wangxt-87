package com.coop.quota.domain;

/**
 * 账本条目类型。金额一律带符号存储，可用余额 = 全部条目之和：
 *   配额口径   = ALLOCATION + TRANSFER_IN + TRANSFER_OUT
 *   在途占用   = -(RESERVATION + RESERVATION_RELEASE)
 *   实捕核销   = -ACTUAL_DEDUCTION
 */
public enum LedgerType {
    /** 初始配额核拨（正） */
    ALLOCATION,
    /** 调拨流入（正） */
    TRANSFER_IN,
    /** 调拨流出（负） */
    TRANSFER_OUT,
    /** 航次申报占用预计额度（负） */
    RESERVATION,
    /** 核实/结案后释放占用（正） */
    RESERVATION_RELEASE,
    /** 靠港核实后的实扣（负） */
    ACTUAL_DEDUCTION
}
