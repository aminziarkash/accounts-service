package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.IdempotencyRecordRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import java.math.BigDecimal;
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
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

// a short timeout so the test is fast; production uses 3s from application.yaml
@SpringBootTest(
        properties = "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '500ms'")
@Import(TestcontainersConfiguration.class)
class LockTimeoutTest {

    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long accountId;

    @BeforeEach
    void setUp() {
        cleanUp();
        Customer customer =
                customerRepository.save(new Customer("lock@test.com", "Lock", "Timeout"));
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

    @Test
    void aBusyAccountFailsFastInsteadOfWaitingForever() throws Exception {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        // another transaction takes the row lock and keeps it
        Future<?> holder =
                pool.submit(() ->
                        transactionTemplate.executeWithoutResult(status -> {
                            accountRepository.findByIdForUpdate(accountId);
                            lockHeld.countDown();
                            try {
                                release.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }));
        assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();

        long start = System.nanoTime();
        assertThatThrownBy(() -> accountService.deposit(accountId, new BigDecimal("10.00")))
                .isInstanceOf(PessimisticLockingFailureException.class);
        long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(waitedMillis).isLessThan(5_000);
        // the failed deposit changed nothing
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("0");
    }
}
