package com.az.accounts.service.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ViewMapperTest {

    private final ViewMapper mapper = Mappers.getMapper(ViewMapper.class);

    @Test
    void mapsAccountAndFlattensCustomerId() {
        Customer customer = new Customer("map@test.com", "Map", "Per");
        Account account = new Account("USD");
        customer.addAccount(account);
        account.deposit(new BigDecimal("25.00"));

        AccountView response = mapper.toView(account);

        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.balance()).isEqualByComparingTo("25.00");
        assertThat(response.customerId()).isEqualTo(customer.getId()); // null: not saved, fine
    }

    @Test
    void mapsCustomerWithItsAccountsAndNoCycle() {
        Customer customer = new Customer("map2@test.com", "Map", "Per");
        customer.addAccount(new Account("USD"));
        customer.addAccount(new Account("EUR"));

        CustomerView response = mapper.toView(customer);

        assertThat(response.email()).isEqualTo("map2@test.com");
        assertThat(response.accounts())
                .extracting(AccountView::currency)
                .containsExactly("USD", "EUR");
    }
}
