package com.coop.quota.domain;

public enum LandingStatus {
    /** 待确认：称重未核实，不计入最终捕捞量 */
    PENDING,
    /** 已核实：已转为实扣 */
    VERIFIED
}
