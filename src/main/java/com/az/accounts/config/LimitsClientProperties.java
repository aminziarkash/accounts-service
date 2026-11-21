package com.az.accounts.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled switch the limits check off completely (default true; tests turn it off)
 * @param baseUrl where limits-service listens (default http://localhost:8100)
 * @param connectTimeout how long to wait for a connection (default 500 ms)
 * @param readTimeout how long to wait for the answer once connected (default 1 second)
 */
@ConfigurationProperties(prefix = "app.limits-client")
public record LimitsClientProperties(
        Boolean enabled, String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public LimitsClientProperties {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        baseUrl = baseUrl == null ? "http://localhost:8100" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofMillis(500) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(1) : readTimeout;
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("app.limits-client.base-url must not be blank");
        }
        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("app.limits-client.connect-timeout must be above 0");
        }
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("app.limits-client.read-timeout must be above 0");
        }
    }
}
