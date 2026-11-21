package com.az.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ChaosStateTest {

    private final ChaosState state = new ChaosState();

    @Test
    void doesNothingByDefault() {
        assertThatCode(state::apply).doesNotThrowAnyException();
    }

    @Test
    void failingAnswers503() {
        state.update(Duration.ZERO, true);

        assertThatThrownBy(state::apply)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void aDelaySlowsTheCall() {
        state.update(Duration.ofMillis(100), false);

        long start = System.nanoTime();
        state.apply();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(100));
    }

    @Test
    void resetClearsEverything() {
        state.update(Duration.ofSeconds(5), true);

        state.reset();

        assertThat(state.delay()).isEqualTo(Duration.ZERO);
        assertThat(state.failing()).isFalse();
        assertThatCode(state::apply).doesNotThrowAnyException();
    }
}