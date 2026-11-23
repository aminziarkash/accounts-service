package com.az.accounts.client;

import com.az.accounts.exception.LimitsUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The limits client with a retry around a circuit breaker. Retry is outside, so every attempt
 * counts for the breaker, and once the breaker opens the retries stop at once (no retry storm).
 */
@Component
public class ResilientLimitsClient {

    private final LimitsClient client;
    private final CircuitBreaker breaker;
    private final Retry retry;

    public ResilientLimitsClient(
            LimitsClient client, CircuitBreaker limitsCircuitBreaker, Retry limitsRetry) {
        this.client = client;
        this.breaker = limitsCircuitBreaker;
        this.retry = limitsRetry;
    }

    public LimitsCheckResponse check(LimitsCheckRequest request) {
        Supplier<LimitsCheckResponse> guarded =
                CircuitBreaker.decorateSupplier(breaker, () -> client.check(request));
        Supplier<LimitsCheckResponse> withRetry = Retry.decorateSupplier(retry, guarded);
        try {
            return withRetry.get();
        } catch (CallNotPermittedException e) {
            // breaker open: fail closed, instantly, without touching the network
            throw new LimitsUnavailableException("The limits circuit breaker is open", e);
        }
    }
}
