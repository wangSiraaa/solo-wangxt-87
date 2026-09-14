package com.coop.quota.domain;

public enum ShortfallStatus {
    /** 待处理：账户可用为负，等待结转或处置；不自动撤销任何合法航次 */
    PENDING,
    /** 已处理：缺口已通过结转等方式补足 */
    RESOLVED
}
