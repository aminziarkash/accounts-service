package com.az.accounts.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IdempotentTransferService {

    private final AccountService accountService;
    private final IdempotencyService idempotency;

    public IdempotentTransferService(
            AccountService accountService, IdempotencyService idempotency) {
        this.accountService = accountService;
        this.idempotency = idempotency;
    }

    public UUID transfer(String key, Long fromId, Long toId, BigDecimal amount) {
        return idempotency.execute(
                key,
                IdempotencyService.hash("TRANSFER", fromId, toId, amount),
                () -> accountService.transfer(fromId, toId, amount),
                UUID::toString,
                UUID::fromString);
    }
}
