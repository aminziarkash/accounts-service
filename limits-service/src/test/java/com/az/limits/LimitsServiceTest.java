package com.az.limits;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LimitsServiceTest {

    private final LimitsService service =
            new LimitsService(
                    new LimitsProperties(
                            new BigDecimal("10000.00"),
                            new BigDecimal("2000.00"),
                            new BigDecimal("5000.00")));

    private static CheckRequest request(Operation operation, String amount) {
        return new CheckRequest(1L, "USD", new BigDecimal(amount), operation);
    }

    @Test
    void aSmallAmountIsApproved() {
        assertThat(service.check(request(Operation.TRANSFER, "100.00")).allowed()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"DEPOSIT,10000.01", "WITHDRAW,2000.01", "TRANSFER,5000.01"})
    void anAmountAboveTheLimitIsDenied(Operation operation, String amount) {
        CheckResponse response = service.check(request(operation, amount));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({"DEPOSIT,10000.00", "WITHDRAW,2000", "TRANSFER,5000.00"})
    void exactlyTheLimitIsApprovedEvenWithAnotherScale(Operation operation, String amount) {
        assertThat(service.check(request(operation, amount)).allowed()).isTrue();
    }

    @Test
    void eachOperationHasItsOwnLimit() {
        assertThat(service.check(request(Operation.WITHDRAW, "3000.00")).allowed()).isFalse();
        assertThat(service.check(request(Operation.TRANSFER, "3000.00")).allowed()).isTrue();
    }

    @Test
    void theReasonNamesTheOperationAndTheLimit() {
        CheckResponse response = service.check(request(Operation.WITHDRAW, "2500.00"));

        assertThat(response.reason()).contains("WITHDRAW").contains("2000.00");
    }
}