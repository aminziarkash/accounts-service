package com.az.accounts.service;

import com.az.accounts.audit.Audited;
import com.az.accounts.domain.Account;
import com.az.accounts.domain.AggregateType;
import com.az.accounts.domain.Customer;
import com.az.accounts.domain.EventType;
import com.az.accounts.domain.LedgerEntry;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.CustomerNotFoundException;
import com.az.accounts.exception.InvalidOperationException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import com.az.accounts.service.event.MoneyDeposited;
import com.az.accounts.service.event.MoneyWithdrawn;
import com.az.accounts.service.event.TransferCompleted;
import com.az.accounts.service.view.AccountView;
import com.az.accounts.service.view.LedgerEntryView;
import com.az.accounts.service.view.ViewMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    // same key as CorrelationIdFilter; the service must not depend on the web package
    private static final String CORRELATION_ID = "correlationId";

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final CustomerRepository customerRepository;
    private final OutboxService outboxService;
    private final ViewMapper mapper;

    public AccountService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            CustomerRepository customerRepository,
            OutboxService outboxService,
            ViewMapper mapper) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.customerRepository = customerRepository;
        this.outboxService = outboxService;
        this.mapper = mapper;
    }

    @Audited("DEPOSIT")
    @Transactional
    public LedgerEntryView deposit(Long accountId, BigDecimal amount) {
        Account account = lockAccount(accountId);
        LedgerEntry entry = account.deposit(amount);
        LedgerEntryView ledgerEntryView = mapper.toView(ledgerEntryRepository.save(entry));
        MoneyDeposited payload =
                new MoneyDeposited(
                        accountId,
                        ledgerEntryView.id(),
                        ledgerEntryView.amount(),
                        account.getCurrency(),
                        ledgerEntryView.balanceAfter(),
                        Instant.now(),
                        MDC.get(CORRELATION_ID),
                        MoneyDeposited.SCHEMA_VERSION);
        outboxService.record(
                AggregateType.ACCOUNT,
                String.valueOf(
                        accountId), // this is the Kafka message key so all events for 1 account
                // stay in order
                EventType.MONEY_DEPOSITED,
                payload);
        return ledgerEntryView;
    }

    @Audited("WITHDRAW")
    @Transactional
    public LedgerEntryView withdraw(Long accountId, BigDecimal amount) {
        Account account = lockAccount(accountId);
        LedgerEntry entry = account.withdraw(amount);
        LedgerEntryView ledgerEntryView = mapper.toView(ledgerEntryRepository.save(entry));
        MoneyWithdrawn payload =
                new MoneyWithdrawn(
                        accountId,
                        ledgerEntryView.id(),
                        ledgerEntryView.amount(),
                        account.getCurrency(),
                        ledgerEntryView.balanceAfter(),
                        Instant.now());
        outboxService.record(
                AggregateType.ACCOUNT,
                String.valueOf(accountId),
                EventType.MONEY_WITHDRAWN,
                payload);
        return ledgerEntryView;
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

        // record the event in the outbox
        TransferCompleted payload =
                new TransferCompleted(
                        transferReference,
                        fromId,
                        toId,
                        amount,
                        first.getCurrency(),
                        Instant.now());
        outboxService.record(
                AggregateType.ACCOUNT,
                String.valueOf(fromId),
                EventType.TRANSFER_COMPLETED,
                payload);

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
