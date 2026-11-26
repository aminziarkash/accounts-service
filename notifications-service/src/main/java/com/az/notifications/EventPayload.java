package com.az.notifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Only the fields this service needs. Everything else in the JSON is ignored (tolerant reader).
 *
 * <p>correlationId and schemaVersion arrived with version 2 of MoneyDeposited. Events written
 * before that do not have them, so both can be null. A null schemaVersion means version 1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventPayload(
        Long accountId,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        Long fromAccountId,
        Long toAccountId,
        String correlationId,
        Integer schemaVersion) {}