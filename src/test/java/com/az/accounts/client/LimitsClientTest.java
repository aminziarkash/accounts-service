package com.az.accounts.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.config.LimitsClientConfig;
import com.az.accounts.config.LimitsClientProperties;
import com.az.accounts.exception.LimitsUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LimitsClientTest {

    private static final String PATH = "/api/v1/limits/check";
    private static final LimitsCheckRequest REQUEST =
            new LimitsCheckRequest(1L, "EUR", new BigDecimal("100.00"), LimitedOperation.WITHDRAW);

    @RegisterExtension
    static WireMockExtension limits =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    // the real client with the real timeouts logic, only shorter so the tests stay fast
    private static LimitsClient clientFor(String baseUrl) {
        LimitsClientProperties properties =
                new LimitsClientProperties(
                        true, baseUrl, Duration.ofMillis(300), Duration.ofMillis(500));
        return new LimitsClient(new LimitsClientConfig().limitsRestClient(properties));
    }

    private static LimitsClient client() {
        return clientFor(limits.baseUrl());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void allowedAnswerIsReturned() {
        limits.stubFor(
                post(urlEqualTo(PATH))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"allowed\":true,\"reason\":null}")));

        LimitsCheckResponse answer = client().check(REQUEST);

        assertThat(answer.allowed()).isTrue();
    }

    @Test
    void deniedAnswerIsAnAnswerNotAnError() {
        limits.stubFor(
                post(urlEqualTo(PATH))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"allowed\":false,\"reason\":\"WITHDRAW above the limit of 2000.00\"}")));

        LimitsCheckResponse answer = client().check(REQUEST);

        assertThat(answer.allowed()).isFalse();
        assertThat(answer.reason()).isEqualTo("WITHDRAW above the limit of 2000.00");
    }

    @Test
    void sendsTheExpectedRequest() {
        limits.stubFor(
                post(urlEqualTo(PATH))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"allowed\":true}")));

        client().check(REQUEST);

        limits.verify(
                1,
                postRequestedFor(urlEqualTo(PATH))
                        .withHeader("Content-Type", equalTo("application/json"))
                        .withRequestBody(matchingJsonPath("$.accountId", equalTo("1")))
                        .withRequestBody(matchingJsonPath("$.currency", equalTo("EUR")))
                        .withRequestBody(matchingJsonPath("$.operation", equalTo("WITHDRAW"))));
    }

    @Test
    void serverErrorMeansNoAnswer() {
        limits.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client().check(REQUEST))
                .isInstanceOf(LimitsUnavailableException.class);
    }

    @Test
    void emptyBodyMeansNoAnswer() {
        limits.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200)));

        assertThatThrownBy(() -> client().check(REQUEST))
                .isInstanceOf(LimitsUnavailableException.class);
    }

    @Test
    void slowAnswerHitsTheReadTimeoutAndDoesNotWaitForTheEnd() {
        limits.stubFor(
                post(urlEqualTo(PATH))
                        .willReturn(
                                aResponse()
                                        .withFixedDelay(3000)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"allowed\":true}")));

        long start = System.nanoTime();
        assertThatThrownBy(() -> client().check(REQUEST))
                .isInstanceOf(LimitsUnavailableException.class);
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // read timeout is 500 ms: we gave up long before the 3 seconds were over
        assertThat(millis).isLessThan(2000);
    }

    @Test
    void nobodyListeningMeansNoAnswer() throws IOException {
        LimitsClient client = clientFor("http://localhost:" + freePort());

        assertThatThrownBy(() -> client.check(REQUEST))
                .isInstanceOf(LimitsUnavailableException.class);
    }
}
