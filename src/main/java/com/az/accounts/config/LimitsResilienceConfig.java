package com.az.accounts.config;

import com.az.accounts.exception.LimitsUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LimitsResilienceProperties.class)
public class LimitsResilienceConfig {

    public static final String NAME = "limits";

    @Bean
    public CircuitBreaker limitsCircuitBreaker(
            LimitsResilienceProperties properties, MeterRegistry meters) {
        CircuitBreakerConfig config =
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(properties.breakerWindowSize())
                        .minimumNumberOfCalls(properties.breakerMinCalls())
                        .failureRateThreshold(properties.breakerFailureRatePercent())
                        .waitDurationInOpenState(properties.breakerOpenDuration())
                        .permittedNumberOfCallsInHalfOpenState(properties.breakerHalfOpenCalls())
                        // only "no answer" is a failure; a denial is a healthy answer
                        .recordExceptions(LimitsUnavailableException.class)
                        .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry.circuitBreaker(NAME);
    }

    @Bean
    public Retry limitsRetry(LimitsResilienceProperties properties, MeterRegistry meters) {
        RetryConfig config =
                RetryConfig.custom()
                        .maxAttempts(properties.retryMaxAttempts())
                        // wait grows x2 each time, randomised by 50% so clients do not retry in
                        // step
                        .intervalFunction(
                                IntervalFunction.ofExponentialRandomBackoff(
                                        properties.retryWait(), 2.0, 0.5))
                        // only transient failures; an open breaker is never retried
                        .retryExceptions(LimitsUnavailableException.class)
                        .build();
        RetryRegistry registry = RetryRegistry.of(config);
        TaggedRetryMetrics.ofRetryRegistry(registry).bindTo(meters);
        return registry.retry(NAME);
    }
}
