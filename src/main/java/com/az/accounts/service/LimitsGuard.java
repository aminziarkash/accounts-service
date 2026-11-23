package com.az.accounts.service;

import com.az.accounts.client.LimitedOperation;
import com.az.accounts.client.LimitsCheckRequest;
import com.az.accounts.client.LimitsCheckResponse;
import com.az.accounts.client.ResilientLimitsClient;
import com.az.accounts.config.LimitsClientProperties;
import com.az.accounts.domain.Account;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.LimitExceededException;
import com.az.accounts.repository.AccountRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Asks the limits service before money moves. Must be called outside any database transaction, so a
 * slow limits service never holds row locks.
 */
@Service
public class LimitsGuard {

    private final ResilientLimitsClient client;
    private final AccountRepository accounts;
    private final LimitsClientProperties properties;

    public LimitsGuard(
            ResilientLimitsClient client,
            AccountRepository accounts,
            LimitsClientProperties properties) {
        this.client = client;
        this.accounts = accounts;
        this.properties = properties;
    }

    public void requireAllowed(LimitedOperation operation, Long accountId, BigDecimal amount) {
        if (!properties.enabled()) {
            return;
        }
        // an unknown account is a plain 404, before any remote call
        Account account =
                accounts.findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));
        LimitsCheckResponse answer =
                client.check(
                        new LimitsCheckRequest(
                                accountId, account.getCurrency(), amount, operation));
        if (!answer.allowed()) {
            throw new LimitExceededException(answer.reason());
        }
    }
}
