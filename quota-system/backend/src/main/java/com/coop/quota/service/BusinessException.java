package com.coop.quota.service;

/** 业务规则冲突（额度不足、维度不匹配、状态非法等），映射为 HTTP 409。 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
