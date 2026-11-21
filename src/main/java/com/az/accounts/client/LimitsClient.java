package com.az.accounts.client;

import com.az.accounts.exception.LimitsUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Asks limits-service whether an operation is allowed. Failures to get an answer become one
 * exception.
 */
@Component
public class LimitsClient {

    private final RestClient restClient;

    public LimitsClient(@Qualifier("limitsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public LimitsCheckResponse check(LimitsCheckRequest request) {
        try {
            LimitsCheckResponse response =
                    restClient
                            .post()
                            .uri("/api/v1/limits/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(LimitsCheckResponse.class);
            if (response == null) {
                throw new LimitsUnavailableException("The limits service sent an empty answer");
            }
            return response;
        } catch (ResourceAccessException | HttpServerErrorException e) {
            // timeout, connection refused, or a 5xx: we have no answer
            throw new LimitsUnavailableException("The limits service did not answer", e);
        }
    }
}
