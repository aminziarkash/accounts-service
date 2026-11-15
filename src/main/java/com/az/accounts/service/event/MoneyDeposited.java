package com.az.accounts.service.event;

import java.math.BigDecimal;
import java.time.Instant;

public record MoneyDeposited(
        Long accountId,
        Long ledgerEntryId,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        Instant occurredAt) {}
