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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotentTransferServiceTest {

    @Autowired private IdempotentTransferService transferService;
    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;

    private Long fromId;
    private Long toId;

    @BeforeEach
    void setUp() {
        cleanUp();
        Customer customer =
                customerRepository.save(new Customer("idem@test.com", "Idem", "Potent"));
        Account from = new Account("USD");
        customer.addAccount(from);
        fromId = accountRepository.save(from).getId();
        Account to = new Account("USD");
        customer.addAccount(to);
        toId = accountRepository.save(to).getId();

        accountService.deposit(fromId, new BigDecimal("100.00"));
    }

    @AfterEach
    void cleanUp() {
        idempotencyRecordRepository.deleteAllInBatch();
        ledgerEntryRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    private BigDecimal balance(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    @Test
    void replayWithSameKeyAndBodyReturnsTheSameResultAndMovesMoneyOnce() {
        UUID first = transferService.transfer("key-1", fromId, toId, new BigDecimal("10.00"));
        UUID replay = transferService.transfer("key-1", fromId, toId, new BigDecimal("10.00"));

        assertThat(replay).isEqualTo(first);
        assertThat(balance(fromId)).isEqualByComparingTo("90.00");
        assertThat(balance(toId)).isEqualByComparingTo("10.00");
    }

    @Test
    void sameAmountWrittenDifferentlyIsTheSameRequest() {
        UUID first = transferService.transfer("key-2", fromId, toId, new BigDecimal("10.00"));
        UUID replay = transferService.transfer("key-2", fromId, toId, new BigDecimal("10.0"));

        assertThat(replay).isEqualTo(first);
        assertThat(balance(fromId)).isEqualByComparingTo("90.00");
    }

    @Test
    void sameKeyWithDifferentBodyIsRejectedAndMovesNothingMore() {
        transferService.transfer("key-3", fromId, toId, new BigDecimal("10.00"));

        assertThatThrownBy(() ->
                        transferService.transfer("key-3", fromId, toId, new BigDecimal("20.00")))
                .isInstanceOf(IdempotencyKeyReuseException.class);

        assertThat(balance(fromId)).isEqualByComparingTo("90.00");
    }

    @Test
    void differentKeysAreDifferentTransfers() {
        transferService.transfer("key-a", fromId, toId, new BigDecimal("10.00"));
        transferService.transfer("key-b", fromId, toId, new BigDecimal("10.00"));

        assertThat(balance(fromId)).isEqualByComparingTo("80.00");
        assertThat(balance(toId)).isEqualByComparingTo("20.00");
    }

    @Test
    void failedTransferIsNotStoredSoTheKeyCanBeRetried() {
        assertThatThrownBy(() ->
                        transferService.transfer("key-4", fromId, toId, new BigDecimal("1000.00")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(idempotencyRecordRepository.findById("key-4")).isEmpty();

        UUID retry = transferService.transfer("key-4", fromId, toId, new BigDecimal("10.00"));
        assertThat(retry).isNotNull();
        assertThat(balance(fromId)).isEqualByComparingTo("90.00");
    }

    @Test
    void concurrentDuplicatesMoveMoneyOnce() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UUID>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(
                    pool.submit(() -> {
                        start.await();
                        return transferService.transfer(
                                "key-race", fromId, toId, new BigDecimal("10.00"));
                    }));
        }
        start.countDown();

        Set<UUID> references = new HashSet<>();
        for (Future<UUID> future : futures) {
            references.add(future.get(30, TimeUnit.SECONDS)); // rethrows any failure
        }
        pool.shutdown();

        assertThat(references).hasSize(1); // everyone got the same stored result
        assertThat(balance(fromId)).isEqualByComparingTo("90.00");
        assertThat(balance(toId)).isEqualByComparingTo("10.00");
    }
}
