package com.coop.quota.dto;

import java.math.BigDecimal;

/** 一行物种分类（按物种代码 + 重量），用于核实时初始分类与分类修订。 */
public record ComponentInput(String speciesCode, BigDecimal weight) {}
