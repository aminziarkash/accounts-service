package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.az.accounts.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxMetricsTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void gaugesShowThePendingCountAndTheAgeOfTheOldestEvent() {
        when(repository.countByPublishedAtIsNull()).thenReturn(3L);
        when(repository.findOldestPendingCreatedAt()).thenReturn(Instant.now().minusSeconds(120));
        new OutboxMetrics(repository).bindTo(registry);

        assertThat(registry.get("outbox.events.pending").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("outbox.oldest.pending.age").gauge().value())
                .isBetween(119.0, 130.0);
    }

    @Test
    void ageIsZeroWhenNothingIsWaiting() {
        when(repository.countByPublishedAtIsNull()).thenReturn(0L);
        when(repository.findOldestPendingCreatedAt()).thenReturn(null);
        new OutboxMetrics(repository).bindTo(registry);

        assertThat(registry.get("outbox.oldest.pending.age").gauge().value()).isZero();
    }
}
