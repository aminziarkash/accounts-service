package com.az.accounts.service.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferCompleted(
        UUID transferReference,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {}
