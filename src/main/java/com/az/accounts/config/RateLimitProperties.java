package com.az.accounts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled switch the limiter off completely (default true)
 * @param capacity the burst size: how many requests a caller can make in a row (default 10)
 * @param refillPerSecond how many requests per second a caller gets back over time (default 0.5)
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Boolean enabled, Integer capacity, Double refillPerSecond) {

    public RateLimitProperties {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        capacity = capacity == null ? 10 : capacity;
        refillPerSecond = refillPerSecond == null ? 0.5 : refillPerSecond;
        if (capacity < 1) {
            throw new IllegalArgumentException("app.rate-limit.capacity must be at least 1");
        }
        if (refillPerSecond <= 0) {
            throw new IllegalArgumentException("app.rate-limit.refill-per-second must be above 0");
        }
    }
}
