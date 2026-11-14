package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import com.az.accounts.exception.IdempotencyKeyReuseException;
import com.az.accounts.exception.InsufficientFundsException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.IdempotencyRecordRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import com.az.accounts.service.view.LedgerEntryView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotentAccountServiceTest {

    private static final CurrentUser USER = new CurrentUser("user-1", false);

    @Autowired private IdempotentAccountService service;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        cleanUp();
        Customer customer = customerRepository.save(new Customer("idem2@test.com", "Idem", "Two"));
        Account account = new Account("USD");
        customer.addAccount(account);
        accountId = accountRepository.save(account).getId();
    }

    @AfterEach
    void cleanUp() {
        idempotencyRecordRepository.deleteAllInBatch();
        ledgerEntryRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    private BigDecimal balance() {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    @Test
    void depositReplayReturnsTheSameEntryAndDepositsOnce() {
        LedgerEntryView first = service.deposit(USER, "d-1", accountId, new BigDecimal("50.00"));
        LedgerEntryView replay = service.deposit(USER, "d-1", accountId, new BigDecimal("50.00"));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.amount()).isEqualByComparingTo("50.00");
        assertThat(balance()).isEqualByComparingTo("50.00");
        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
    }

    @Test
    void withdrawReplayReturnsTheSameEntryAndWithdrawsOnce() {
        service.deposit(USER, "d-2", accountId, new BigDecimal("100.00"));

        LedgerEntryView first = service.withdraw(USER, "w-1", accountId, new BigDecimal("30.00"));
        LedgerEntryView replay = service.withdraw(USER, "w-1", accountId, new BigDecimal("30.00"));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(balance()).isEqualByComparingTo("70.00");
    }

    @Test
    void sameAmountWrittenDifferentlyIsTheSameRequest() {
        LedgerEntryView first = service.deposit(USER, "d-3", accountId, new BigDecimal("10.00"));
        LedgerEntryView replay = service.deposit(USER, "d-3", accountId, new BigDecimal("10.0"));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(balance()).isEqualByComparingTo("10.00");
    }

    @Test
    void sameKeyWithDifferentAmountIsRejected() {
        service.deposit(USER, "d-4", accountId, new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.deposit(USER, "d-4", accountId, new BigDecimal("20.00")))
                .isInstanceOf(IdempotencyKeyReuseException.class);

        assertThat(balance()).isEqualByComparingTo("10.00");
    }

    @Test
    void sameKeyForADifferentOperationIsRejected() {
        service.deposit(USER, "shared-key", accountId, new BigDecimal("10.00"));

        // same key and amount, but a withdrawal is not the same request as a deposit
        assertThatThrownBy(() ->
                        service.withdraw(USER, "shared-key", accountId, new BigDecimal("10.00")))
                .isInstanceOf(IdempotencyKeyReuseException.class);

        assertThat(balance()).isEqualByComparingTo("10.00");
    }

    @Test
    void failedWithdrawalIsNotStoredSoTheKeyCanBeRetried() {
        assertThatThrownBy(() -> service.withdraw(USER, "w-2", accountId, new BigDecimal("5.00")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(idempotencyRecordRepository.findById("w-2")).isEmpty();

        service.deposit(USER, "d-5", accountId, new BigDecimal("20.00"));
        LedgerEntryView retry = service.withdraw(USER, "w-2", accountId, new BigDecimal("5.00"));

        assertThat(retry.id()).isNotNull();
        assertThat(balance()).isEqualByComparingTo("15.00");
    }

    @Test
    void sameKeyFromDifferentCallersIsNotTheSameRequest() {
        CurrentUser other = new CurrentUser("someone-else", false);

        LedgerEntryView first =
                service.deposit(USER, "shared-key", accountId, new BigDecimal("10.00"));
        LedgerEntryView second =
                service.deposit(other, "shared-key", accountId, new BigDecimal("10.00"));

        // no replay across callers: nobody can read or reuse someone else's stored result
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(balance()).isEqualByComparingTo("20.00");
    }

    @Test
    void concurrentDuplicateDepositsDepositOnce() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<LedgerEntryView>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(
                    pool.submit(() -> {
                        start.await();
                        return service.deposit(USER, "d-race", accountId, new BigDecimal("10.00"));
                    }));
        }
        start.countDown();

        Set<Long> entryIds = new HashSet<>();
        for (Future<LedgerEntryView> future : futures) {
            entryIds.add(future.get(30, TimeUnit.SECONDS).id()); // rethrows any failure
        }
        pool.shutdown();

        assertThat(entryIds).hasSize(1); // everyone got the same stored entry
        assertThat(balance()).isEqualByComparingTo("10.00");
        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
    }
}
