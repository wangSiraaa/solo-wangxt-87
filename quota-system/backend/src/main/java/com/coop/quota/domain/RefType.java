package com.coop.quota.domain;

/**
 * 账本条目关联的业务单据类型，用于从余额追溯到航次或调拨记录。
 */
public enum RefType {
    ALLOCATION,
    TRANSFER,
    VOYAGE,
    LANDING
}
