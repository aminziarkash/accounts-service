package com.az.accounts.service;

import com.az.accounts.client.LimitedOperation;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IdempotentTransferService {

    private final AccountService accountService;
    private final IdempotencyService idempotency;
    private final LimitsGuard limits;

    public IdempotentTransferService(
            AccountService accountService, IdempotencyService idempotency, LimitsGuard limits) {
        this.accountService = accountService;
        this.idempotency = idempotency;
        this.limits = limits;
    }

    public UUID transfer(CurrentUser user, String key, Long fromId, Long toId, BigDecimal amount) {
        return idempotency.execute(
                IdempotencyService.scope(user.subject(), key),
                IdempotencyService.hash("TRANSFER", fromId, toId, amount),
                () -> limits.requireAllowed(LimitedOperation.TRANSFER, fromId, amount),
                () -> accountService.transfer(fromId, toId, amount),
                UUID::toString,
                UUID::fromString);
    }
}
