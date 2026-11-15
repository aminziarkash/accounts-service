package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.exception.InvalidOperationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxIntegrationTest {

    @Autowired CustomerService customerService;

    @Autowired AccountService accountService;

    @Autowired JdbcTemplate jdbc;

    private Long newAccount() {
        var customer =
                customerService.create("user-1", UUID.randomUUID() + "@test.com", "Test", "User");
        return accountService.openAccount(customer.id(), "EUR").id();
    }

    private List<Map<String, Object>> eventsFor(Long accountId) {
        return jdbc.queryForList(
                "select event_type, payload, published_at from outbox_events where aggregate_id = ?",
                String.valueOf(accountId));
    }

    @Test
    void depositWritesOneUnpublishedEvent() {
        Long accountId = newAccount();

        accountService.deposit(accountId, new BigDecimal("25.00"));

        var rows = eventsFor(accountId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("event_type")).isEqualTo("MoneyDeposited");
        assertThat(rows.get(0).get("published_at")).isNull();
        assertThat(rows.get(0).get("payload").toString()).contains("25.00");
    }

    @Test
    void rejectedDepositWritesNoEvent() {
        Long accountId = newAccount();

        assertThatThrownBy(() -> accountService.deposit(accountId, new BigDecimal("-5")))
                .isInstanceOf(InvalidOperationException.class);

        assertThat(eventsFor(accountId)).isEmpty();
    }
}
