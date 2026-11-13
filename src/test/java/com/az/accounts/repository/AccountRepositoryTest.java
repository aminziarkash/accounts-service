package com.az.accounts.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import com.az.accounts.domain.LedgerEntry;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountRepositoryTest {

    @Autowired private EntityManager entityManager;

    @Autowired private AccountRepository accountRepository;

    @Autowired private CustomerRepository customerRepository;

    @Autowired private LedgerEntryRepository ledgerEntryRepository;

    @Test
    public void savesAccountWithLedgerEntriesAndReadsThemBack() {
        Customer customer = new Customer("abc@test.com", "Amin", "Ziarkash");
        customerRepository.save(customer);

        Account account = new Account("USD");
        customer.addAccount(account);

        accountRepository.save(account);

        BigDecimal depositAmount = new BigDecimal("250.3");
        BigDecimal withdrawalAmount = new BigDecimal("15.00");
        ledgerEntryRepository.save(account.deposit(depositAmount));
        ledgerEntryRepository.save(account.withdraw(withdrawalAmount));

        entityManager.flush();
        entityManager.clear();

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("235.30");

        Page<LedgerEntry> entries =
                ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(
                        account.getId(), PageRequest.of(0, 20));
        assertThat(entries.getTotalElements()).isEqualTo(2);
    }

    @Test
    public void findAccountsByCustomerId() {
        Customer customer1 = new Customer("customer1@test.com", "Customer", "One");
        customerRepository.save(customer1);
        Customer customer2 = new Customer("customer2@test.com", "Customer", "Two");
        customerRepository.save(customer2);
        Customer customer3 = new Customer("customer3@test.com", "Customer", "Three");
        customerRepository.save(customer3);

        Account account1Customer1 = new Account("USD");
        Account account2Customer1 = new Account("USD");
        customer1.addAccount(account1Customer1);
        accountRepository.save(account1Customer1);
        customer1.addAccount(account2Customer1);
        accountRepository.save(account2Customer1);

        Account account1Customer2 = new Account("EUR");
        customer2.addAccount(account1Customer2);
        accountRepository.save(account1Customer2);

        entityManager.flush();
        entityManager.clear();

        assertThat(accountRepository.findByCustomerId(customer1.getId()))
                .containsExactlyInAnyOrder(account1Customer1, account2Customer1);
        assertThat(accountRepository.findByCustomerId(customer2.getId()))
                .containsExactly(account1Customer2);
        assertThat(accountRepository.findByCustomerId(customer3.getId())).isEmpty();
    }

    @Test
    void returnsLedgerEntriesNewestFirstWithPaging() throws InterruptedException {
        Customer customer = new Customer("paging@test.com", "Paging", "Test");
        customerRepository.save(customer);
        Account account = new Account("USD");
        customer.addAccount(account);
        accountRepository.save(account);

        // deposits of 10, 20, 30, 40, 50 give balances 10, 30, 60, 100, 150
        for (int amount = 10; amount <= 50; amount += 10) {
            ledgerEntryRepository.save(account.deposit(new BigDecimal(amount)));
            entityManager.flush();
            Thread.sleep(5); // make createdAt differ so the ordering is deterministic
        }
        entityManager.clear();

        Page<LedgerEntry> firstPage =
                ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(
                        account.getId(), PageRequest.of(0, 2));
        assertThat(firstPage.getContent())
                .extracting(entry -> entry.getBalanceAfter().intValueExact())
                .containsExactly(150, 100);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);

        Page<LedgerEntry> lastPage =
                ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(
                        account.getId(), PageRequest.of(2, 2));
        assertThat(lastPage.getContent())
                .extracting(entry -> entry.getBalanceAfter().intValueExact())
                .containsExactly(10);
    }
}
