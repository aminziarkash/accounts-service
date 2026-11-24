package com.az.notifications;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AccountEventsListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEventsListener.class);

    private final NotificationService notificationService;

    public AccountEventsListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${app.events.topic}")
    public void onEvent(ConsumerRecord<String, String> record) {
        String eventId = header(record, "event-id");
        String eventType = header(record, "event-type");
        if (eventId == null || eventType == null) {
            throw new IllegalArgumentException(
                    "Missing event-id or event-type header at partition %d offset %d"
                            .formatted(record.partition(), record.offset()));
        }
        log.info("Received {} id={} key={} partition={} offset={}",
                eventType, eventId, record.key(), record.partition(), record.offset());
        String sequence = header(record, "event-seq");
        notificationService.handle(UUID.fromString(eventId), eventType, record.value(), record.key(),
                sequence == null ? null : Long.valueOf(sequence));
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}