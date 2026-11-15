package com.az.notifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Only the fields this service needs. Everything else in the JSON is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventPayload(
        Long accountId,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        Long fromAccountId,
        Long toAccountId) {}