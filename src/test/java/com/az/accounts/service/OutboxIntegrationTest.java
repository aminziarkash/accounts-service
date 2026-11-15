package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.exception.InsufficientFundsException;
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
        assertThat(rows.get(0).get("event_type")).isEqualTo("MONEY_DEPOSITED");
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

    @Test
    void withdrawWritesOneUnpublishedEvent() {
        Long accountId = newAccount();
        accountService.deposit(accountId, new BigDecimal("100.00"));

        accountService.withdraw(accountId, new BigDecimal("30.00"));

        var rows =
                jdbc.queryForList(
                        "select payload, published_at from outbox_events"
                                + " where aggregate_id = ? and event_type = 'MONEY_WITHDRAWN'",
                        String.valueOf(accountId));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("published_at")).isNull();
        assertThat(rows.get(0).get("payload").toString()).contains("30.00").contains("70.00");
    }

    @Test
    void rejectedWithdrawWritesNoEvent() {
        Long accountId = newAccount();

        assertThatThrownBy(() -> accountService.withdraw(accountId, new BigDecimal("10.00")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(eventsFor(accountId)).isEmpty();
    }

    @Test
    void transferWritesOneEventForTheWholeTransfer() {
        Long from = newAccount();
        Long to = newAccount();
        accountService.deposit(from, new BigDecimal("100.00"));

        UUID reference = accountService.transfer(from, to, new BigDecimal("40.00"));

        var rows =
                jdbc.queryForList(
                        "select aggregate_id, payload from outbox_events where event_type = 'TRANSFER_COMPLETED'"
                                + " and payload like ?",
                        "%" + reference + "%");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("aggregate_id")).isEqualTo(String.valueOf(from));
        assertThat(rows.get(0).get("payload").toString())
                .contains("40.00")
                .contains("\"toAccountId\":" + to);
    }

    @Test
    void rejectedTransferWritesNoTransferEvent() {
        Long from = newAccount();
        Long to = newAccount();

        assertThatThrownBy(() -> accountService.transfer(from, to, new BigDecimal("10.00")))
                .isInstanceOf(InsufficientFundsException.class);

        var rows =
                jdbc.queryForList(
                        "select 1 from outbox_events where event_type = 'TRANSFER_COMPLETED'"
                                + " and aggregate_id = ?",
                        String.valueOf(from));
        assertThat(rows).isEmpty();
    }
}
