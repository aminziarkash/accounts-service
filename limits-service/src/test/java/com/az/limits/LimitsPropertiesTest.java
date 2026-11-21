package com.az.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LimitsPropertiesTest {

    @Test
    void missingValuesGetDefaults() {
        LimitsProperties properties = new LimitsProperties(null, null, null);

        assertThat(properties.maxDeposit()).isEqualByComparingTo("10000");
        assertThat(properties.maxWithdrawal()).isEqualByComparingTo("2000");
        assertThat(properties.maxTransfer()).isEqualByComparingTo("5000");
    }

    @Test
    void aLimitOfZeroOrLessIsRejectedAtStartup() {
        assertThatThrownBy(() -> new LimitsProperties(BigDecimal.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.limits.max-deposit");
        assertThatThrownBy(() -> new LimitsProperties(null, null, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.limits.max-transfer");
    }
}