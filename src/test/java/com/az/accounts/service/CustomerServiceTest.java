package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.CustomerNotFoundException;
import com.az.accounts.exception.DuplicateEmailException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import com.az.accounts.service.view.AccountView;
import com.az.accounts.service.view.CustomerView;
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
        CustomerView created = customerService.create("owner-1", email, "Jane", "Doe");

        assertThat(created.id()).isNotNull();
        assertThatThrownBy(() -> customerService.create("owner-1", email, "Other", "Person"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void getReturnsCustomerWithAccountsLoaded() {
        CustomerView customer = customerService.create("owner-1", newEmail(), "Jane", "Doe");
        accountService.openAccount(customer.id(), "USD");
        accountService.openAccount(customer.id(), "EUR");

        CustomerView loaded = customerService.get(customer.id());

        // no open session here: this only works because the accounts were fetched
        assertThat(loaded.accounts()).hasSize(2);
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
        CustomerView customer = customerService.create("owner-1", newEmail(), "Jane", "Doe");
        AccountView account = accountService.openAccount(customer.id(), "USD");
        accountService.deposit(account.id(), new BigDecimal("10.00"));
        accountService.deposit(account.id(), new BigDecimal("5.00"));

        var page = accountService.getLedger(account.id(), PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThatThrownBy(() -> accountService.getLedger(-1L, PageRequest.of(0, 20)))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
