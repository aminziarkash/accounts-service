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
}
