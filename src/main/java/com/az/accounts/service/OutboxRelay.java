package com.az.accounts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.outbox.relay.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxPublisher publisher;

    public OutboxRelay(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT0.5S}")
    public void run() {
        int published = publisher.publishBatch();
        if (published > 0) {
            log.debug("Published {} outbox events", published);
        }
    }
}
