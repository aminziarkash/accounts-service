package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import com.az.accounts.domain.LedgerEntry;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.InsufficientFundsException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountServiceTest {

    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;

    private Long accountId;
    private Long otherAccountId;

    @BeforeEach
    void setUp() {
        cleanUp();
        Customer customer =
                customerRepository.save(new Customer("service@test.com", "Service", "Test"));
        Account account = new Account("USD");
        customer.addAccount(account);
        accountId = accountRepository.save(account).getId();

        Account other = new Account("USD");
        customer.addAccount(other);
        otherAccountId = accountRepository.save(other).getId();
    }

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    @Test
    void depositUpdatesBalanceAndWritesLedgerEntryTogether() {
        accountService.deposit(accountId, new BigDecimal("100"));

        Account reloaded = accountRepository.findById(accountId).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("100");

        Page<LedgerEntry> entries =
                ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(
                        accountId, PageRequest.of(0, 10));
        assertThat(entries.getTotalElements()).isEqualTo(1);
        assertThat(entries.getContent().get(0).getBalanceAfter()).isEqualByComparingTo("100");
    }

    @Test
    void invalidAmountChangesNothing() {
        assertThatThrownBy(() -> accountService.deposit(accountId, new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class);

        Account reloaded = accountRepository.findById(accountId).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("0");
        assertThat(ledgerEntryRepository.count()).isZero();
    }

    @Test
    void unknownAccountThrows() {
        assertThatThrownBy(() -> accountService.deposit(999_999L, new BigDecimal("10")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void withdrawReducesBalanceAndWritesLedgerEntry() {
        accountService.deposit(accountId, new BigDecimal("100"));

        accountService.withdraw(accountId, new BigDecimal("30"));

        Account reloaded = accountRepository.findById(accountId).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("70");
        Page<LedgerEntry> entries =
                ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(
                        accountId, PageRequest.of(0, 10));
        assertThat(entries.getTotalElements()).isEqualTo(2);
    }

    @Test
    void withdrawingMoreThanBalanceChangesNothing() {
        accountService.deposit(accountId, new BigDecimal("50"));

        assertThatThrownBy(() -> accountService.withdraw(accountId, new BigDecimal("50.01")))
                .isInstanceOf(InsufficientFundsException.class);

        Account reloaded = accountRepository.findById(accountId).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("50");
        assertThat(ledgerEntryRepository.count()).isEqualTo(1); // only the deposit
    }

    @Test
    void withdrawingTheWholeBalanceIsAllowed() {
        accountService.deposit(accountId, new BigDecimal("50"));

        accountService.withdraw(accountId, new BigDecimal("50"));

        Account reloaded = accountRepository.findById(accountId).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void withdrawFromUnknownAccountThrows() {
        assertThatThrownBy(() -> accountService.withdraw(999_999L, new BigDecimal("10")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void transferMovesMoneyAndWritesTwoLinkedEntries() {
        accountService.deposit(accountId, new BigDecimal("100"));

        UUID ref = accountService.transfer(accountId, otherAccountId, new BigDecimal("40"));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("60");
        assertThat(accountRepository.findById(otherAccountId).orElseThrow().getBalance())
                .isEqualByComparingTo("40");
        long linked =
                ledgerEntryRepository.findAll().stream()
                        .filter(e -> ref.equals(e.getTransferReference()))
                        .count();
        assertThat(linked).isEqualTo(2);
    }

    @Test
    void transferWithInsufficientFundsChangesNothing() {
        accountService.deposit(accountId, new BigDecimal("10"));

        assertThatThrownBy(() ->
                        accountService.transfer(accountId, otherAccountId, new BigDecimal("50")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("10");
        assertThat(accountRepository.findById(otherAccountId).orElseThrow().getBalance())
                .isEqualByComparingTo("0");
        assertThat(ledgerEntryRepository.count()).isEqualTo(1); // only the first deposit
    }

    @Test
    void transferToTheSameAccountIsRejected() {
        accountService.deposit(accountId, new BigDecimal("10"));

        assertThatThrownBy(() -> accountService.transfer(accountId, accountId, new BigDecimal("5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transferToAnUnknownAccountThrows() {
        accountService.deposit(accountId, new BigDecimal("10"));

        assertThatThrownBy(() -> accountService.transfer(accountId, 999_999L, new BigDecimal("5")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void concurrentDepositsKeepBalanceAndLedgerConsistent() throws Exception {
        int threads = 20;
        int depositsPerThread = 5;
        BigDecimal amount = new BigDecimal("1.00");
        BigDecimal expected = amount.multiply(BigDecimal.valueOf(threads * depositsPerThread));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(
                    pool.submit(() -> {
                        start.await(); // all threads begin at the same moment
                        for (int i = 0; i < depositsPerThread; i++) {
                            accountService.deposit(accountId, amount);
                        }
                        return null;
                    }));
        }
        start.countDown();
        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS); // rethrows any exception from a thread
        }
        pool.shutdown();

        Account reloaded = accountRepository.findById(accountId).orElseThrow();
        BigDecimal ledgerSum =
                ledgerEntryRepository.findAll().stream()
                        .map(LedgerEntry::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(ledgerSum).isEqualByComparingTo(expected);
        assertThat(reloaded.getBalance()).isEqualByComparingTo(expected);
    }

    @Test
    void oppositeTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        accountService.deposit(accountId, new BigDecimal("1000"));
        accountService.deposit(otherAccountId, new BigDecimal("1000"));

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            boolean forward = t % 2 == 0; // half A to B, half B to A
            futures.add(
                    pool.submit(() -> {
                        start.await();
                        for (int i = 0; i < 10; i++) {
                            if (forward) {
                                accountService.transfer(accountId, otherAccountId, BigDecimal.ONE);
                            } else {
                                accountService.transfer(otherAccountId, accountId, BigDecimal.ONE);
                            }
                        }
                        return null;
                    }));
        }
        start.countDown();
        for (Future<Void> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        BigDecimal total =
                accountRepository
                        .findById(accountId)
                        .orElseThrow()
                        .getBalance()
                        .add(accountRepository.findById(otherAccountId).orElseThrow().getBalance());
        assertThat(total).isEqualByComparingTo("2000");
    }
}
