package com.az.accounts.service;

import com.az.accounts.domain.Account;
import com.az.accounts.domain.LedgerEntry;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import java.math.BigDecimal;
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
}
