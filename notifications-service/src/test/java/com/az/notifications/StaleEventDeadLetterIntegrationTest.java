package com.az.notifications;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StaleEventDeadLetterIntegrationTest {

    private static final String DEPOSIT =
            """
            {"accountId":3,"ledgerEntryId":5,"amount":100.00,"currency":"USD",\
            "balanceAfter":100.00,"occurredAt":"2026-11-15T15:26:30Z"}""";

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ConsumerFactory<String, String> consumerFactory;
    @Autowired NotificationRepository notifications;
    @Autowired MeterRegistry registry;
    @Value("${app.events.topic}") String topic;

    private ProducerRecord<String, String> event(String key, UUID eventId, long sequence, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        record.headers().add("event-id", eventId.toString().getBytes(UTF_8));
        record.headers().add("event-type", "MONEY_DEPOSITED".getBytes(UTF_8));
        record.headers().add("event-seq", String.valueOf(sequence).getBytes(UTF_8));
        return record;
    }

    private boolean stored(UUID eventId) {
        return notifications.findAll().stream().anyMatch(n -> n.getEventId().equals(eventId));
    }

    @Test
    void aStaleEventGoesToTheDeadLetterTopicAndTheNextEventStillWorks() throws Exception {
        String key = "stale-" + UUID.randomUUID(); // its own key, so other tests cannot interfere
        UUID newer = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        UUID after = UUID.randomUUID();
        String stalePayload = DEPOSIT.replace("100.00", "777.77"); // easy to recognise in the DLT
        double deadBefore =
                registry.counter("notifications.events.dead.lettered", "reason", "OutOfOrderEventException")
                        .count();

        kafkaTemplate.send(event(key, newer, 200, DEPOSIT)).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(event(key, stale, 100, stalePayload)).get(10, TimeUnit.SECONDS); // older, but arrives later
        kafkaTemplate.send(event(key, after, 300, DEPOSIT)).get(10, TimeUnit.SECONDS);

        // the partition kept moving: the event behind the stale one is stored
        await().atMost(Duration.ofSeconds(30)).until(() -> stored(after));
        assertThat(stored(newer)).isTrue();
        assertThat(stored(stale)).isFalse(); // the stale event was not applied
        assertThat(registry.counter("notifications.events.dead.lettered", "reason", "OutOfOrderEventException")
                .count() - deadBefore)
                .isEqualTo(1.0);

        // and it is waiting in the dead-letter topic
        List<String> deadLetters = new ArrayList<>();
        try (Consumer<String, String> consumer =
                     consumerFactory.createConsumer("dlt-test-" + UUID.randomUUID(), "")) {
            consumer.subscribe(List.of(topic + ".DLT"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> deadLetters.add(record.value()));
                assertThat(deadLetters).anyMatch(value -> value.contains("777.77"));
            });
        }
    }
}