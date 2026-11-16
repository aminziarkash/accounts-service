package com.az.accounts.service;

import com.az.accounts.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics implements MeterBinder {

    private final OutboxEventRepository repository;

    public OutboxMetrics(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(
                        "outbox.events.pending",
                        repository,
                        OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox events waiting to be published to Kafka")
                .register(registry);
        Gauge.builder("outbox.oldest.pending.age", this, OutboxMetrics::oldestPendingAgeSeconds)
                .baseUnit("seconds")
                .description("Age of the oldest outbox event that is still waiting")
                .register(registry);
    }

    double oldestPendingAgeSeconds() {
        Instant oldest = repository.findOldestPendingCreatedAt();
        return oldest == null ? 0 : Duration.between(oldest, Instant.now()).toMillis() / 1000.0;
    }
}
