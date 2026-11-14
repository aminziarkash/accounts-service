package com.az.accounts.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static <T> java.util.List<String> fields(Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
    }

    @Test
    void validCustomerRequestHasNoViolations() {
        var request = new CreateCustomerRequest("jane@test.com", "Jane", "Doe");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void customerRequestRejectsBlankNamesAndBadEmail() {
        var request = new CreateCustomerRequest("not-an-email", " ", "");

        assertThat(fields(validator.validate(request)))
                .containsExactly("email", "firstName", "lastName");
    }

    @Test
    void amountMustBePositiveWithAtMostTwoDecimals() {
        assertThat(validator.validate(new AmountRequest(new BigDecimal("10.50")))).isEmpty();

        assertThat(fields(validator.validate(new AmountRequest(BigDecimal.ZERO))))
                .containsExactly("amount");
        assertThat(fields(validator.validate(new AmountRequest(new BigDecimal("-5")))))
                .containsExactly("amount");
        assertThat(fields(validator.validate(new AmountRequest(new BigDecimal("1.234")))))
                .containsExactly("amount");
        assertThat(fields(validator.validate(new AmountRequest(null)))).containsExactly("amount");
    }

    @Test
    void currencyMustBeThreeUppercaseLetters() {
        assertThat(validator.validate(new OpenAccountRequest(1L, "USD"))).isEmpty();

        assertThat(fields(validator.validate(new OpenAccountRequest(1L, "usd"))))
                .containsExactly("currency");
        assertThat(fields(validator.validate(new OpenAccountRequest(1L, "US"))))
                .containsExactly("currency");
        assertThat(fields(validator.validate(new OpenAccountRequest(null, "USD"))))
                .containsExactly("customerId");
    }

    @Test
    void transferRequiresBothAccountsAndAPositiveAmount() {
        var request = new TransferRequest(null, -1L, new BigDecimal("0"));

        assertThat(fields(validator.validate(request)))
                .containsExactly("amount", "fromAccountId", "toAccountId");
    }
}
