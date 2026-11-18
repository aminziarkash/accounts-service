package com.az.accounts.service;

import com.az.accounts.config.RateLimitProperties;
import com.az.accounts.exception.RateLimitExceededException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Keeps one token bucket per caller and says no when a caller's bucket is empty. The buckets live
 * in memory, so the limit is per instance of the service.
 */
@Component
public class RateLimiter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Takes one token for this caller, or throws when none is left.
     *
     * @param callerId The caller ID
     */
    public void check(String callerId) {
        if (!properties.enabled()) {
            return;
        }
        TokenBucket bucket =
                buckets.computeIfAbsent(
                        callerId,
                        id ->
                                new TokenBucket(
                                        properties.capacity(),
                                        properties.refillPerSecond(),
                                        clock));
        if (!bucket.tryConsume()) {
            throw new RateLimitExceededException(bucket.secondsUntilNextToken());
        }
    }
}
