package com.az.accounts.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.config.LimitsClientConfig;
import com.az.accounts.config.LimitsClientProperties;
import com.az.accounts.config.LimitsResilienceConfig;
import com.az.accounts.config.LimitsResilienceProperties;
import com.az.accounts.exception.LimitsUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LimitsResilienceTest {

    private static final String PATH = "/api/v1/limits/check";
    private static final LimitsCheckRequest REQUEST =
            new LimitsCheckRequest(1L, "EUR", new BigDecimal("100.00"), LimitedOperation.WITHDRAW);

    @RegisterExtension
    static WireMockExtension limits =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private record Setup(CircuitBreaker breaker, ResilientLimitsClient client) {}

    // window of 4, opens at 50% failures, stays open 300 ms, 2 test calls when half-open
    private static Setup setup(int attempts) {
        LimitsResilienceProperties properties =
                new LimitsResilienceProperties(
                        attempts, Duration.ofMillis(10), 4, 4, 50, Duration.ofMillis(300), 2);
        LimitsResilienceConfig config = new LimitsResilienceConfig();
        CircuitBreaker breaker = config.limitsCircuitBreaker(properties, new SimpleMeterRegistry());
        Retry retry = config.limitsRetry(properties, new SimpleMeterRegistry());
        LimitsClient raw =
                new LimitsClient(
                        new LimitsClientConfig()
                                .limitsRestClient(
                                        new LimitsClientProperties(
                                                true,
                                                limits.baseUrl(),
                                                Duration.ofMillis(300),
                                                Duration.ofMillis(500))));
        return new Setup(breaker, new ResilientLimitsClient(raw, breaker, retry));
    }

    private static void stubServerError() {
        limits.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(500)));
    }

    private static void stubAllowed() {
        limits.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("{\"allowed\":true}")));
    }

    private static void failOnce(Setup setup) {
        assertThatThrownBy(() -> setup.client().check(REQUEST))
                .isInstanceOf(LimitsUnavailableException.class);
    }

    private static void openTheBreaker(Setup setup) {
        stubServerError();
        for (int i = 0; i < 4; i++) {
            failOnce(setup);
        }
        assertThat(setup.breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void oneBadAnswerIsRetriedAndTheCallSucceeds() {
        limits.stubFor(
                post(urlEqualTo(PATH))
                        .inScenario("flaky")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withStatus(500))
                        .willSetStateTo("recovered"));
        limits.stubFor(
                post(urlEqualTo(PATH))
                        .inScenario("flaky")
                        .whenScenarioStateIs("recovered")
                        .willReturn(okJson("{\"allowed\":true}")));
        Setup setup = setup(3);

        LimitsCheckResponse answer = setup.client().check(REQUEST);

        assertThat(answer.allowed()).isTrue();
        limits.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void retryGivesUpAfterTheMaximumAttempts() {
        stubServerError();
        Setup setup = setup(3);

        failOnce(setup);

        limits.verify(3, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void aDenialIsNotRetriedAndNotAFailure() {
        limits.stubFor(
                post(urlEqualTo(PATH))
                        .willReturn(okJson("{\"allowed\":false,\"reason\":\"above the limit\"}")));
        Setup setup = setup(3);

        LimitsCheckResponse answer = setup.client().check(REQUEST);

        assertThat(answer.allowed()).isFalse();
        limits.verify(1, postRequestedFor(urlEqualTo(PATH)));
        assertThat(setup.breaker().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(setup.breaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void breakerOpensAfterEnoughFailuresAndThenFailsFastWithoutTheNetwork() {
        Setup setup = setup(1);
        openTheBreaker(setup);
        limits.verify(4, postRequestedFor(urlEqualTo(PATH)));

        long start = System.nanoTime();
        assertThatThrownBy(() -> setup.client().check(REQUEST))
                .isInstanceOf(LimitsUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(millis).isLessThan(200);
        limits.verify(4, postRequestedFor(urlEqualTo(PATH))); // still 4: nothing was sent
    }

    @Test
    void retriesStopAsSoonAsTheBreakerOpens() {
        stubServerError();
        Setup setup = setup(3);

        failOnce(setup); // 3 attempts, 3 failures recorded
        failOnce(setup); // attempt 4 fails and opens the breaker, attempt 5 is refused, no retry
        // storm

        limits.verify(4, postRequestedFor(urlEqualTo(PATH)));
        assertThat(setup.breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void afterTheWaitTheBreakerTestsAndClosesAgainWhenTheServiceIsBack() throws Exception {
        Setup setup = setup(1);
        openTheBreaker(setup);

        stubAllowed();
        Thread.sleep(400); // longer than the 300 ms open duration

        assertThat(setup.client().check(REQUEST).allowed()).isTrue(); // half-open test call 1
        assertThat(setup.client().check(REQUEST).allowed()).isTrue(); // half-open test call 2

        assertThat(setup.breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void ifTheTestCallsFailTheBreakerOpensAgain() throws Exception {
        Setup setup = setup(1);
        openTheBreaker(setup);

        Thread.sleep(400); // service is still broken
        failOnce(setup);
        failOnce(setup);

        assertThat(setup.breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
