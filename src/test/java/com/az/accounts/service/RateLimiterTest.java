package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.config.RateLimitProperties;
import com.az.accounts.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    // time never moves in these tests, so no token ever comes back
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private RateLimiter limiterWithCapacity(int capacity) {
        return new RateLimiter(new RateLimitProperties(true, capacity, 1.0), clock);
    }

    @Test
    void rejectsTheCallAfterTheBucketIsEmpty() {
        RateLimiter limiter = limiterWithCapacity(2);
        limiter.check("alice");
        limiter.check("alice");

        assertThatThrownBy(() -> limiter.check("alice"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(e ->
                        assertThat(((RateLimitExceededException) e).getRetryAfterSeconds())
                                .isEqualTo(1));
    }

    @Test
    void eachCallerHasTheirOwnBucket() {
        RateLimiter limiter = limiterWithCapacity(1);
        limiter.check("alice");

        assertThatThrownBy(() -> limiter.check("alice"))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.check("bob")).doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenSwitchedOff() {
        RateLimiter limiter = new RateLimiter(new RateLimitProperties(false, 1, 1.0), clock);

        assertThatCode(
                        () -> {
                            for (int i = 0; i < 100; i++) {
                                limiter.check("alice");
                            }
                        })
                .doesNotThrowAnyException();
    }
}
