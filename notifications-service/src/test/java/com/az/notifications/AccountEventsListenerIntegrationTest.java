package com.az.notifications;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountEventsListenerIntegrationTest {

    private static final String DEPOSIT =
            """
            {"accountId":3,"ledgerEntryId":5,"amount":100.00,"currency":"USD",\
            "balanceAfter":100.00,"occurredAt":"2026-11-15T15:26:30Z"}""";

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired NotificationRepository notifications;
    @Value("${app.events.topic}") String topic;

    private ProducerRecord<String, String> record(UUID eventId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "3", DEPOSIT);
        record.headers().add("event-id", eventId.toString().getBytes(UTF_8));
        record.headers().add("event-type", "MONEY_DEPOSITED".getBytes(UTF_8));
        return record;
    }

    private long countFor(UUID eventId) {
        return notifications.findAll().stream().filter(n -> n.getEventId().equals(eventId)).count();
    }

    @Test
    void aDuplicateDeliveryThroughKafkaCreatesOneNotification() throws Exception {
        UUID duplicated = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();
        var event = record(duplicated);

        // the same event delivered twice, then a different one behind it (same key, same partition)
        kafkaTemplate.send(event).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(event).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(record(sentinel)).get(10, TimeUnit.SECONDS);

        // the sentinel is behind both copies, so once it is stored the duplicates were handled
        await().atMost(Duration.ofSeconds(30)).until(() -> countFor(sentinel) == 1);
        assertThat(countFor(duplicated)).isEqualTo(1);
    }
}