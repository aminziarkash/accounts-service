package com.az.accounts.service.view;

import com.az.accounts.domain.LedgerEntryType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryView(
        Long id,
        LedgerEntryType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID transferReference,
        Instant createdAt) {}
