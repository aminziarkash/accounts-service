package com.az.accounts.service;

import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import com.az.accounts.domain.LedgerEntry;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.CustomerNotFoundException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final CustomerRepository customerRepository;

    public AccountService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            CustomerRepository customerRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public LedgerEntry deposit(Long accountId, BigDecimal amount) {
        Account account = lockAccount(accountId);
        LedgerEntry entry = account.deposit(amount);
        return ledgerEntryRepository.save(entry);
    }

    @Transactional
    public LedgerEntry withdraw(Long accountId, BigDecimal amount) {
        Account account = lockAccount(accountId);
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

    @Transactional
    public Account openAccount(Long customerId, String currency) {
        Customer customer =
                customerRepository
                        .findById(customerId)
                        .orElseThrow(() -> new CustomerNotFoundException(customerId));
        Account account = new Account(currency);
        customer.addAccount(account);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> getLedger(Long accountId, Pageable pageable) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    private Account lockAccount(Long id) {
        return accountRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
