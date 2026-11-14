package com.az.accounts.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param retention how long a stored key is kept; a retry after this is treated as a new request
 * @param cleanupInterval how often the cleanup job runs
 */
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(Duration retention, Duration cleanupInterval) {

    public IdempotencyProperties {
        retention = retention == null ? Duration.ofHours(24) : retention;
        cleanupInterval = cleanupInterval == null ? Duration.ofHours(1) : cleanupInterval;
    }
}
