package com.az.accounts.service;

import com.az.accounts.repository.LedgerEntryRepository;
import com.az.accounts.service.view.LedgerEntryView;
import com.az.accounts.service.view.ViewMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Deposit and withdraw that are safe to repeat with the same Idempotency-Key. */
@Service
public class IdempotentAccountService {

    private final AccountService accountService;
    private final IdempotencyService idempotency;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ViewMapper mapper;

    public IdempotentAccountService(
            AccountService accountService,
            IdempotencyService idempotency,
            LedgerEntryRepository ledgerEntryRepository,
            ViewMapper mapper) {
        this.accountService = accountService;
        this.idempotency = idempotency;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.mapper = mapper;
    }

    public LedgerEntryView deposit(
            CurrentUser user, String key, Long accountId, BigDecimal amount) {
        return idempotency.execute(
                IdempotencyService.scope(user.subject(), key),
                IdempotencyService.hash("DEPOSIT", accountId, amount),
                () -> accountService.deposit(accountId, amount),
                entry -> String.valueOf(entry.id()),
                this::loadEntry);
    }

    public LedgerEntryView withdraw(
            CurrentUser user, String key, Long accountId, BigDecimal amount) {
        return idempotency.execute(
                IdempotencyService.scope(user.subject(), key),
                IdempotencyService.hash("WITHDRAW", accountId, amount),
                () -> accountService.withdraw(accountId, amount),
                entry -> String.valueOf(entry.id()),
                this::loadEntry);
    }

    // a replay returns the ledger entry the first request created
    private LedgerEntryView loadEntry(String ref) {
        return ledgerEntryRepository
                .findById(Long.valueOf(ref))
                .map(mapper::toView)
                .orElseThrow(
                        () -> new IllegalStateException("Ledger entry " + ref + " is missing"));
    }
}
