package com.az.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.Logger;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificationServiceTest {

    private static final String DEPOSIT =
            """
            {"accountId":3,"ledgerEntryId":5,"amount":100.00,"currency":"USD",\
            "balanceAfter":100.00,"occurredAt":"2026-11-15T15:26:30Z"}""";

    private static final String TRANSFER =
            """
            {"transferReference":"6f0c3b0e-5a52-4d65-9b57-0d6c6c2a1f11","fromAccountId":7,\
            "toAccountId":9,"amount":40.00,"currency":"USD","occurredAt":"2026-11-15T15:30:00Z"}""";

    private static final String DEPOSIT_V2 =
            """
            {"accountId":3,"ledgerEntryId":6,"amount":50.00,"currency":"USD",\
            "balanceAfter":150.00,"occurredAt":"2026-11-15T15:27:30Z",\
            "correlationId":"req-42","schemaVersion":2}""";

    private static final String DEPOSIT_FROM_THE_FUTURE =
            """
            {"accountId":3,"ledgerEntryId":7,"amount":10.00,"currency":"USD",\
            "balanceAfter":160.00,"occurredAt":"2026-11-15T15:28:30Z",\
            "correlationId":"req-43","schemaVersion":3,"channel":"MOBILE","tags":["a","b"]}""";

    @Autowired NotificationService service;
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry registry;

    @BeforeEach
    void clean() {
        jdbc.update("delete from notifications");
        jdbc.update("delete from processed_events");
        jdbc.update("delete from account_sequences");
    }

    private int processedCount() {
        return jdbc.queryForObject("select count(*) from processed_events", Integer.class);
    }

    // counters keep their value across tests in the same context, so tests compare differences
    private double count(String name) {
        return registry.counter(name).count();
    }

    @Test
    void onlyARedeliveredEventIsCountedAsADuplicate() {
        double before = count("notifications.events.duplicates");
        UUID id = UUID.randomUUID();

        service.handle(id, "MONEY_DEPOSITED", DEPOSIT); // fresh
        assertThat(count("notifications.events.duplicates") - before).isZero();

        service.handle(id, "MONEY_DEPOSITED", DEPOSIT); // redelivered
        assertThat(count("notifications.events.duplicates") - before).isEqualTo(1.0);
    }

    @Test
    void onlyAnUnknownEventTypeIsCountedAsSkipped() {
        double before = count("notifications.events.skipped");

        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT);
        assertThat(count("notifications.events.skipped") - before).isZero();

        service.handle(UUID.randomUUID(), "SOMETHING_NEW", "{}");
        assertThat(count("notifications.events.skipped") - before).isEqualTo(1.0);
    }

    @Test
    void depositEventCreatesOneNotification() {
        UUID id = UUID.randomUUID();

        service.handle(id, "MONEY_DEPOSITED", DEPOSIT);

        var all = notifications.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getEventId()).isEqualTo(id);
        assertThat(all.get(0).getAccountId()).isEqualTo(3L);
        assertThat(all.get(0).getMessage()).contains("100.00").contains("USD").contains("account 3");
    }

    @Test
    void transferEventIsStoredUnderTheSourceAccount() {
        service.handle(UUID.randomUUID(), "TRANSFER_COMPLETED", TRANSFER);

        var all = notifications.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getAccountId()).isEqualTo(7L);
        assertThat(all.get(0).getMessage()).contains("from account 7 to account 9");
    }

    @Test
    void theSameEventTwiceCreatesOneNotification() {
        UUID id = UUID.randomUUID();

        service.handle(id, "MONEY_DEPOSITED", DEPOSIT);
        service.handle(id, "MONEY_DEPOSITED", DEPOSIT);

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(processedCount()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicatesCreateOneNotification() throws Exception {
        UUID id = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                service.handle(id, "MONEY_DEPOSITED", DEPOSIT);
                return null;
            }));
        }

        start.countDown();
        for (Future<Object> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void anUnknownEventTypeIsSkippedButRemembered() {
        service.handle(UUID.randomUUID(), "SOMETHING_NEW", "{}");

        assertThat(notifications.count()).isZero();
        assertThat(processedCount()).isEqualTo(1);
    }

    @Test
    void unknownFieldsInThePayloadAreIgnored() {
        String withExtraField = DEPOSIT.replace("\"currency\"", "\"futureField\":\"x\",\"currency\"");

        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", withExtraField);

        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void aBrokenPayloadRollsBackTheDedupeRowSoARetryCanSucceed() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.handle(id, "MONEY_DEPOSITED", "not json"))
                .isInstanceOf(RuntimeException.class);
        assertThat(processedCount()).isZero();

        service.handle(id, "MONEY_DEPOSITED", DEPOSIT);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void aNewerEventForTheSameAccountIsApplied() {
        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT, "3", 10L);
        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT, "3", 20L);

        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test
    void anOlderEventForTheSameAccountIsRefusedAndNotMarkedProcessed() {
        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT, "3", 20L);
        UUID late = UUID.randomUUID();

        assertThatThrownBy(() -> service.handle(late, "MONEY_DEPOSITED", DEPOSIT, "3", 10L))
                .isInstanceOf(OutOfOrderEventException.class);

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(processedCount()).isEqualTo(1);
    }

    @Test
    void differentAccountsHaveTheirOwnOrder() {
        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT, "3", 20L);
        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT, "4", 10L);

        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test
    void aRedeliveredOldEventIsIgnoredNotRefused() {
        UUID first = UUID.randomUUID();
        service.handle(first, "MONEY_DEPOSITED", DEPOSIT, "3", 10L);
        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT, "3", 20L);

        service.handle(first, "MONEY_DEPOSITED", DEPOSIT, "3", 10L); // no exception

        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test
    void anEventWithoutASequenceIsStillAccepted() {
        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT, "3", null);

        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void aVersion1DepositWithoutTheNewFieldsIsStillHandled() {
        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT);

        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void aVersion2DepositIsHandled() {
        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT_V2);

        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void aFutureVersionWithFieldsWeDoNotKnowIsStillHandled() {
        service.handle(UUID.randomUUID(), "MONEY_DEPOSITED", DEPOSIT_FROM_THE_FUTURE);

        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void theCorrelationIdIsInTheLogsWhileHandlingAndGoneAfterwards() {
        Logger logger = (Logger) LoggerFactory.getLogger(NotificationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // an unknown type logs a warning, which gives us a log line to inspect
            service.handle(UUID.randomUUID(), "SOMETHING_NEW", DEPOSIT_V2);

            assertThat(appender.list)
                    .anySatisfy(
                            e -> {
                                assertThat(e.getFormattedMessage()).contains("Unknown event type");
                                assertThat(e.getMDCPropertyMap()).containsEntry("correlationId", "req-42");
                            });
            assertThat(MDC.get("correlationId")).isNull(); // threads are reused: nothing left behind
        } finally {
            logger.detachAppender(appender);
        }
    }
}