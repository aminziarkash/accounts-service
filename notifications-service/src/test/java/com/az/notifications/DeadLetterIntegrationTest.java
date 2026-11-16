package com.az.notifications;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
class DeadLetterIntegrationTest {

    private static final String DEPOSIT =
            """
            {"accountId":3,"ledgerEntryId":5,"amount":100.00,"currency":"USD",\
            "balanceAfter":100.00,"occurredAt":"2026-11-15T15:26:30Z"}""";

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ConsumerFactory<String, String> consumerFactory;
    @Autowired NotificationRepository notifications;
    @Value("${app.events.topic}") String topic;

    private ProducerRecord<String, String> goodEvent(UUID eventId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "3", DEPOSIT);
        record.headers().add("event-id", eventId.toString().getBytes(UTF_8));
        record.headers().add("event-type", "MONEY_DEPOSITED".getBytes(UTF_8));
        return record;
    }

    @Test
    void aPoisonPillGoesToTheDeadLetterTopicAndDoesNotBlockTheNextEvent() throws Exception {
        UUID next = UUID.randomUUID();
        String poison = "garbage-" + UUID.randomUUID();

        // no headers: the listener cannot process it. Same key, so the good event queues behind it.
        kafkaTemplate.send(new ProducerRecord<>(topic, "3", poison)).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(goodEvent(next)).get(10, TimeUnit.SECONDS);

        // the partition is not blocked: the event behind the poison pill is processed
        await().atMost(Duration.ofSeconds(30))
                .until(() -> notifications.findAll().stream().anyMatch(n -> n.getEventId().equals(next)));

        // and the poison pill is in the dead-letter topic
        List<String> deadLetters = new ArrayList<>();
        try (Consumer<String, String> consumer =
                     consumerFactory.createConsumer("dlt-test-" + UUID.randomUUID(), "")) {
            consumer.subscribe(List.of(topic + ".DLT"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> deadLetters.add(record.value()));
                assertThat(deadLetters).contains(poison);
            });
        }
    }
}