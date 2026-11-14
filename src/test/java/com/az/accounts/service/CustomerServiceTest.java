package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.CustomerNotFoundException;
import com.az.accounts.exception.DuplicateEmailException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CustomerServiceTest {

    @Autowired private CustomerService customerService;
    @Autowired private AccountService accountService;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CustomerRepository customerRepository;

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    private static String newEmail() {
        return UUID.randomUUID() + "@test.com";
    }

    @Test
    void createsCustomerAndRejectsDuplicateEmail() {
        String email = newEmail();
        Customer created = customerService.create(email, "Jane", "Doe");

        assertThat(created.getId()).isNotNull();
        assertThatThrownBy(() -> customerService.create(email, "Other", "Person"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void getReturnsCustomerWithAccountsLoaded() {
        Customer customer = customerService.create(newEmail(), "Jane", "Doe");
        accountService.openAccount(customer.getId(), "USD");
        accountService.openAccount(customer.getId(), "EUR");

        Customer loaded = customerService.get(customer.getId());

        // no open session here: this only works because the accounts were fetched
        assertThat(loaded.getAccounts()).hasSize(2);
    }

    @Test
    void unknownCustomerIsNotFound() {
        assertThatThrownBy(() -> customerService.get(-1L))
                .isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> accountService.openAccount(-1L, "USD"))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void ledgerOfDepositIsPagedAndUnknownAccountIsNotFound() {
        Customer customer = customerService.create(newEmail(), "Jane", "Doe");
        Account account = accountService.openAccount(customer.getId(), "USD");
        accountService.deposit(account.getId(), new BigDecimal("10.00"));
        accountService.deposit(account.getId(), new BigDecimal("5.00"));

        var page = accountService.getLedger(account.getId(), PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThatThrownBy(() -> accountService.getLedger(-1L, PageRequest.of(0, 20)))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
