package com.az.accounts.service;

import com.az.accounts.domain.Account;
import com.az.accounts.domain.LedgerEntry;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountService(
            AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public LedgerEntry deposit(Long accountId, BigDecimal amount) {
        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));
        LedgerEntry entry = account.deposit(amount);
        return ledgerEntryRepository.save(entry);
    }

    @Transactional
    public LedgerEntry withdraw(Long accountId, BigDecimal amount) {
        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));
        LedgerEntry entry = account.withdraw(amount);
        return ledgerEntryRepository.save(entry);
    }

    @Transactional
    public UUID transfer(Long fromId, Long toId, BigDecimal amount) {
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        // lock in a fixed order (lower id first) so opposite transfers cannot deadlock
        Long firstId = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);
        Account first = lockAccount(firstId);
        Account second = lockAccount(secondId);
        Account from = fromId.equals(firstId) ? first : second;
        Account to = fromId.equals(firstId) ? second : first;

        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new IllegalArgumentException("Currency mismatch between accounts");
        }

        UUID transferReference = UUID.randomUUID();
        ledgerEntryRepository.save(from.withdraw(amount, transferReference));
        ledgerEntryRepository.save(to.deposit(amount, transferReference));
        return transferReference;
    }

    private Account lockAccount(Long id) {
        return accountRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
