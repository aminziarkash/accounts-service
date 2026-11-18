package com.az.accounts.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A bucket that holds up to {@code capacity} tokens and gets {@code refillPerSecond} new ones every
 * second. Each request takes one token. An empty bucket means the caller must wait.
 */
public class TokenBucket {

    private final long capacity;
    private final double refillPerSecond;
    private final Clock clock;

    // fractional on purpose: at 0.5 tokens per second, after one second we hold half a token
    private double tokens;
    private Instant lastRefill;

    public TokenBucket(long capacity, double refillPerSecond, Clock clock) {
        if (capacity < 1 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity must be >= 1 and refill rate > 0");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.clock = clock;
        this.tokens = capacity; // starts full, so a new caller can burst right away
        this.lastRefill = clock.instant();
    }

    // synchronized: two requests from the same caller can arrive at the same moment, and without
    // it both could see the same token and both get through
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    /** 0 when a request would be allowed now, otherwise whole seconds to wait (rounded up). */
    public synchronized long secondsUntilNextToken() {
        refill();
        if (tokens >= 1) {
            return 0;
        }
        return (long) Math.ceil((1 - tokens) / refillPerSecond);
    }

    // No background thread: we don't tick every second, we work out afterwards how many tokens
    // would have arrived since the last look. Cheap, and easy to test with a fake clock.
    private void refill() {
        Instant now = clock.instant();
        double seconds = Duration.between(lastRefill, now).toNanos() / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + seconds * refillPerSecond); // never above capacity
        lastRefill = now;
    }
}
