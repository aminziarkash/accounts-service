package com.az.accounts.client;

import java.math.BigDecimal;

public record LimitsCheckRequest(
        Long accountId, String currency, BigDecimal amount, LimitedOperation operation) {}
