package com.az.accounts.service.view;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountView(
        Long id, String currency, BigDecimal balance, Long customerId, Instant createdAt) {}
