package com.az.accounts.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.outbox")
public record OutboxProperties(String topic, Integer batchSize, Duration sendTimeout) {

    public OutboxProperties {
        if (batchSize == null) {
            batchSize = 50;
        }
        if (sendTimeout == null) {
            sendTimeout = Duration.ofSeconds(5);
        }
    }
}
