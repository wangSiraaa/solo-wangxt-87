package com.coop.quota.domain;

public enum VoyageStatus {
    /** 草稿：尚未占用额度，可删除 */
    DRAFT,
    /** 已申报：预计额度已占用 */
    DECLARED,
    /** 已结案：剩余占用已释放 */
    CLOSED,
    /** 已取消：占用已全部释放（无已核实卸货时才允许） */
    CANCELLED
}
