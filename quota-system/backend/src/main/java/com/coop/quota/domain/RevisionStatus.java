package com.coop.quota.domain;

public enum RevisionStatus {
    /** 已应用：当前生效的分类 */
    APPLIED,
    /** 已推翻：调整已全额冲回，分类回退到之前的状态 */
    OVERTURNED
}
