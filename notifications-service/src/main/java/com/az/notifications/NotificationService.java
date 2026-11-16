package com.az.notifications;

import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ProcessedEvents processedEvents;
    private final NotificationRepository notifications;
    private final JsonMapper jsonMapper;
    private final MeterRegistry registry;

    public NotificationService(
            ProcessedEvents processedEvents,
            NotificationRepository notifications,
            JsonMapper jsonMapper,
            MeterRegistry registry) {
        this.processedEvents = processedEvents;
        this.notifications = notifications;
        this.jsonMapper = jsonMapper;
        this.registry = registry;
    }

    @Transactional
    public void handle(UUID eventId, String eventType, String payload) {
        if (!processedEvents.markProcessed(eventId)) {
            log.info("Duplicate event {} ignored", eventId);
            return;
        }
        EventPayload event = jsonMapper.readValue(payload, EventPayload.class);

        registry.counter("notifications.events.duplicates").increment();
        registry.counter("notifications.events.skipped").increment();

        switch (eventType) {
            case "MONEY_DEPOSITED" -> save(eventId, eventType, event.accountId(),
                    "Deposit of %s %s into account %s. New balance: %s"
                            .formatted(event.amount(), event.currency(), event.accountId(), event.balanceAfter()));
            case "MONEY_WITHDRAWN" -> save(eventId, eventType, event.accountId(),
                    "Withdrawal of %s %s from account %s. New balance: %s"
                            .formatted(event.amount(), event.currency(), event.accountId(), event.balanceAfter()));
            case "TRANSFER_COMPLETED" -> save(eventId, eventType, event.fromAccountId(),
                    "Transfer of %s %s from account %s to account %s"
                            .formatted(event.amount(), event.currency(), event.fromAccountId(), event.toAccountId()));
            default -> log.warn("Unknown event type {} for event {}, skipped", eventType, eventId);
        }
    }

    private void save(UUID eventId, String eventType, Long accountId, String message) {
        notifications.save(new Notification(eventId, accountId, eventType, message));
        registry.counter("notifications.events.processed", "type", eventType).increment();
    }
}