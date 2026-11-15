package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.az.accounts.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxPublisherTest {

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    @Autowired OutboxPublisher publisher;
    @Autowired CustomerService customerService;
    @Autowired AccountService accountService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from outbox_events");
    }

    private Long accountWithDeposits(String... amounts) {
        var customer =
                customerService.create("user-1", UUID.randomUUID() + "@test.com", "Test", "User");
        Long accountId = accountService.openAccount(customer.id(), "EUR").id();
        for (String amount : amounts) {
            accountService.deposit(accountId, new BigDecimal(amount));
        }
        return accountId;
    }

    private int publishedCount() {
        return jdbc.queryForObject(
                "select count(*) from outbox_events where published_at is not null", Integer.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesInOrderWithKeyAndHeadersAndMarksPublished() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        Long accountId = accountWithDeposits("10.00", "20.00");

        int published = publisher.publishBatch();

        assertThat(published).isEqualTo(2);
        assertThat(publishedCount()).isEqualTo(2);
        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());
        ProducerRecord<String, String> first = captor.getAllValues().get(0);
        assertThat(first.topic()).isEqualTo("accounts.events");
        assertThat(first.key()).isEqualTo(String.valueOf(accountId));
        assertThat(first.value()).contains("10.00");
        assertThat(first.headers().lastHeader("event-id")).isNotNull();
        assertThat(new String(first.headers().lastHeader("event-type").value()))
                .isEqualTo("MONEY_DEPOSITED");
        assertThat(captor.getAllValues().get(1).value()).contains("20.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopsAtTheFirstFailureAndKeepsTheRestUnpublished() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        accountWithDeposits("10.00", "20.00", "30.00");

        int published = publisher.publishBatch();

        assertThat(published).isEqualTo(1);
        assertThat(publishedCount()).isEqualTo(1);
        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void nothingToPublishDoesNotCallKafka() {
        assertThat(publisher.publishBatch()).isZero();
        verifyNoInteractions(kafkaTemplate);
    }
}
