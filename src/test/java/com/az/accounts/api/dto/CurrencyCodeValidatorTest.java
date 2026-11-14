package com.az.accounts.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CurrencyCodeValidatorTest {

    private final CurrencyCodeValidator validator = new CurrencyCodeValidator();

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "JPY", "GBP"})
    void acceptsRealCurrencyCodes(String code) {
        assertThat(validator.isValid(code, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "ABC", "US", "USDX", "", " "})
    void rejectsEverythingElse(String code) {
        assertThat(validator.isValid(code, null)).isFalse();
    }

    @Test
    void nullIsLeftToNotNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
