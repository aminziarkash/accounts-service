package com.az.accounts.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id, String currency, BigDecimal balance, Long customerId, Instant createdAt) {}
