package com.az.accounts.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.az.accounts.api.dto.AccountResponse;
import com.az.accounts.api.dto.CustomerResponse;
import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class AccountMapperTest {

    private final AccountMapper mapper = Mappers.getMapper(AccountMapper.class);

    @Test
    void mapsAccountAndFlattensCustomerId() {
        Customer customer = new Customer("map@test.com", "Map", "Per");
        Account account = new Account("USD");
        customer.addAccount(account);
        account.deposit(new BigDecimal("25.00"));

        AccountResponse response = mapper.toResponse(account);

        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.balance()).isEqualByComparingTo("25.00");
        assertThat(response.customerId()).isEqualTo(customer.getId()); // null: not saved, fine
    }

    @Test
    void mapsCustomerWithItsAccountsAndNoCycle() {
        Customer customer = new Customer("map2@test.com", "Map", "Per");
        customer.addAccount(new Account("USD"));
        customer.addAccount(new Account("EUR"));

        CustomerResponse response = mapper.toResponse(customer);

        assertThat(response.email()).isEqualTo("map2@test.com");
        assertThat(response.accounts())
                .extracting(AccountResponse::currency)
                .containsExactly("USD", "EUR");
    }
}
