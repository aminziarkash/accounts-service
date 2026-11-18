package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    /** A clock we can move by hand, so the tests never sleep. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();

    @Test
    void allowsABurstUpToCapacityThenRejects() {
        TokenBucket bucket = new TokenBucket(3, 1.0, clock);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillsAsTimePasses() {
        TokenBucket bucket = new TokenBucket(2, 1.0, clock);
        bucket.tryConsume();
        bucket.tryConsume();
        assertThat(bucket.tryConsume()).isFalse();

        clock.advance(Duration.ofSeconds(1)); // one token comes back

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void neverHoldsMoreThanCapacity() {
        TokenBucket bucket = new TokenBucket(2, 1.0, clock);

        clock.advance(Duration.ofHours(1)); // a long quiet time must not save up 3600 tokens

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void tellsTheCallerHowLongToWait() {
        TokenBucket bucket = new TokenBucket(1, 0.5, clock); // one token every 2 seconds
        bucket.tryConsume();

        assertThat(bucket.secondsUntilNextToken()).isEqualTo(2);
    }
}
