package com.az.limits;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** The current "misbehave" settings. Changed at runtime by ChaosController, read on every check. */
@Component
public class ChaosState {

    private volatile Duration delay = Duration.ZERO;
    private volatile boolean failing;

    public void update(Duration delay, boolean failing) {
        this.delay = delay;
        this.failing = failing;
    }

    public void reset() {
        update(Duration.ZERO, false);
    }

    public Duration delay() {
        return delay;
    }

    public boolean failing() {
        return failing;
    }

    /** Waits for the configured delay, then fails with 503 if asked to. Does nothing by default. */
    public void apply() {
        Duration wait = delay;
        if (!wait.isZero()) {
            try {
                Thread.sleep(wait.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (failing) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "simulated failure");
        }
    }
}