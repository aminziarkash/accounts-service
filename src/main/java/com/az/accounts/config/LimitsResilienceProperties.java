package com.az.accounts.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param retryMaxAttempts total tries, including the first (2 means one retry)
 * @param retryWait base wait before a retry; it doubles each time and is randomised
 * @param breakerWindowSize how many of the latest calls the breaker looks at
 * @param breakerMinCalls the breaker never opens before this many calls were seen
 * @param breakerFailureRatePercent open when this share of the window failed
 * @param breakerOpenDuration how long it stays open before testing again
 * @param breakerHalfOpenCalls how many test calls are allowed while half-open
 */
@ConfigurationProperties(prefix = "app.limits-client.resilience")
public record LimitsResilienceProperties(
        Integer retryMaxAttempts,
        Duration retryWait,
        Integer breakerWindowSize,
        Integer breakerMinCalls,
        Integer breakerFailureRatePercent,
        Duration breakerOpenDuration,
        Integer breakerHalfOpenCalls) {

    public LimitsResilienceProperties {
        retryMaxAttempts = retryMaxAttempts == null ? 2 : retryMaxAttempts;
        retryWait = retryWait == null ? Duration.ofMillis(100) : retryWait;
        breakerWindowSize = breakerWindowSize == null ? 10 : breakerWindowSize;
        breakerMinCalls = breakerMinCalls == null ? 5 : breakerMinCalls;
        breakerFailureRatePercent =
                breakerFailureRatePercent == null ? 50 : breakerFailureRatePercent;
        breakerOpenDuration =
                breakerOpenDuration == null ? Duration.ofSeconds(10) : breakerOpenDuration;
        breakerHalfOpenCalls = breakerHalfOpenCalls == null ? 2 : breakerHalfOpenCalls;
        if (retryMaxAttempts < 1) {
            throw new IllegalArgumentException("retry-max-attempts must be at least 1");
        }
        if (retryWait.isNegative()) {
            throw new IllegalArgumentException("retry-wait must not be negative");
        }
        if (breakerWindowSize < 1 || breakerMinCalls < 1 || breakerHalfOpenCalls < 1) {
            throw new IllegalArgumentException("breaker sizes and counts must be at least 1");
        }
        if (breakerFailureRatePercent < 1 || breakerFailureRatePercent > 100) {
            throw new IllegalArgumentException("breaker-failure-rate-percent must be 1 to 100");
        }
        if (breakerOpenDuration.isZero() || breakerOpenDuration.isNegative()) {
            throw new IllegalArgumentException("breaker-open-duration must be above 0");
        }
    }
}
