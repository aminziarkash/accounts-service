package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.az.accounts.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// batch size 1: each relay takes exactly one row, so the two relays share the work
@SpringBootTest(properties = "app.outbox.batch-size=1")
@Import(TestcontainersConfiguration.class)
class OutboxOrderingTest {

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    @Autowired OutboxPublisher publisher;
    @Autowired CustomerService customerService;
    @Autowired AccountService accountService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from outbox_events");
    }

    @Test
    @SuppressWarnings("unchecked")
    void twoRelaysNeverPublishTheEventsOfOneAccountOutOfOrder() throws Exception {
        List<String> sent = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch relayAIsSending = new CountDownLatch(1);
        CountDownLatch letRelayAFinish = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenAnswer(invocation -> {
                    ProducerRecord<String, String> record = invocation.getArgument(0);
                    if (calls.getAndIncrement() == 0) {
                        // relay A: the first send. It holds the lock on row 1 and is slow.
                        relayAIsSending.countDown();
                        if (!letRelayAFinish.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("relay A was never released");
                        }
                    }
                    sent.add(record.value());
                    return CompletableFuture.completedFuture(null);
                });

        var customer =
                customerService.create("user-1", UUID.randomUUID() + "@test.com", "Test", "User");
        Long accountId = accountService.openAccount(customer.id(), "EUR").id();
        accountService.deposit(accountId, new BigDecimal("10.00")); // outbox row 1
        accountService.deposit(accountId, new BigDecimal("20.00")); // outbox row 2, same account

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> relay = publisher::publishBatch;
            Future<Integer> relayA = pool.submit(relay);
            assertThat(relayAIsSending.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> relayB = pool.submit(relay); // runs while A holds row 1
            relayB.get(10, TimeUnit.SECONDS);

            letRelayAFinish.countDown();
            relayA.get(10, TimeUnit.SECONDS);

            assertThat(relayB.get(10, TimeUnit.SECONDS)).isZero(); // B finds the lock taken
        } finally {
            pool.shutdownNow();
        }

        // whatever is still unpublished goes out now, one relay at a time
        while (publisher.publishBatch() > 0) {
            // keep draining
        }

        assertThat(sent).hasSize(2);
        assertThat(sent.get(0)).contains("10.00");
        assertThat(sent.get(1)).contains("20.00");
    }
}
