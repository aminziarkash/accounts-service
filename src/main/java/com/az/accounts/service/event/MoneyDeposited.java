package com.az.accounts.service.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Version 2 adds correlationId and schemaVersion. Both are optional for readers: version 1 events
 * do not have them, and correlationId is null when no web request caused the event.
 */
public record MoneyDeposited(
        Long accountId,
        Long ledgerEntryId,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        Instant occurredAt,
        String correlationId,
        Integer schemaVersion) {

    public static final int SCHEMA_VERSION = 2;
}
