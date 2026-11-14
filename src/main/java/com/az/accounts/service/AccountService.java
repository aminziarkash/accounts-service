package com.az.accounts.service;

import com.az.accounts.audit.Audited;
import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import com.az.accounts.domain.LedgerEntry;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.CustomerNotFoundException;
import com.az.accounts.exception.InvalidOperationException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import com.az.accounts.service.view.AccountView;
import com.az.accounts.service.view.LedgerEntryView;
import com.az.accounts.service.view.ViewMapper;
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
    private final ViewMapper mapper;

    public AccountService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            CustomerRepository customerRepository,
            ViewMapper mapper) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.customerRepository = customerRepository;
        this.mapper = mapper;
    }

    @Audited("DEPOSIT")
    @Transactional
    public LedgerEntryView deposit(Long accountId, BigDecimal amount) {
        Account account = lockAccount(accountId);
        LedgerEntry entry = account.deposit(amount);
        return mapper.toView(ledgerEntryRepository.save(entry));
    }

    @Audited("WITHDRAW")
    @Transactional
    public LedgerEntryView withdraw(Long accountId, BigDecimal amount) {
        Account account = lockAccount(accountId);
        LedgerEntry entry = account.withdraw(amount);
        return mapper.toView(ledgerEntryRepository.save(entry));
    }

    @Audited("TRANSFER")
    @Transactional
    public UUID transfer(Long fromId, Long toId, BigDecimal amount) {
        if (fromId.equals(toId)) {
            throw new InvalidOperationException("Cannot transfer to the same account");
        }
        // lock in a fixed order (lower id first) so opposite transfers cannot deadlock
        Long firstId = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);
        Account first = lockAccount(firstId);
        Account second = lockAccount(secondId);
        Account from = fromId.equals(firstId) ? first : second;
        Account to = fromId.equals(firstId) ? second : first;

        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new InvalidOperationException("Currency mismatch between accounts");
        }

        UUID transferReference = UUID.randomUUID();
        ledgerEntryRepository.save(from.withdraw(amount, transferReference));
        ledgerEntryRepository.save(to.deposit(amount, transferReference));
        return transferReference;
    }

    @Transactional
    public AccountView openAccount(Long customerId, String currency) {
        Customer customer =
                customerRepository
                        .findById(customerId)
                        .orElseThrow(() -> new CustomerNotFoundException(customerId));
        Account account = new Account(currency);
        customer.addAccount(account);
        return mapper.toView(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public AccountView getAccount(Long id) {
        return mapper.toView(
                accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryView> getLedger(Long accountId, Pageable pageable) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return ledgerEntryRepository
                .findByAccountId(accountId, pageable)
                .map(entry -> mapper.toView(entry));
    }

    private Account lockAccount(Long id) {
        return accountRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
