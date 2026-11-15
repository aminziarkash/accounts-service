package com.az.accounts.service;

import com.az.accounts.config.OutboxProperties;
import com.az.accounts.domain.OutboxEvent;
import com.az.accounts.repository.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    public OutboxPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /** Sends one batch in order. Returns how many were published. Stops at the first failure. */
    @Transactional
    public int publishBatch() {
        List<OutboxEvent> batch = repository.lockUnpublishedBatch(properties.batchSize());
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate
                        .send(toRecord(event))
                        .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                log.warn("Could not publish outbox event {}, will retry", event.getEventId(), e);
                break;
            }
            event.markPublished();
            published++;
        }
        return published;
    }

    private ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        properties.topic(), event.getAggregateId(), event.getPayload());
        record.headers().add("event-id", bytes(event.getEventId().toString()));
        record.headers().add("event-type", bytes(event.getEventType().name()));
        return record;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
