package com.az.limits;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param maxDeposit the largest single deposit that is allowed (default 10000.00)
 * @param maxWithdrawal the largest single withdrawal that is allowed (default 2000.00)
 * @param maxTransfer the largest single transfer that is allowed (default 5000.00)
 */
@ConfigurationProperties(prefix = "app.limits")
public record LimitsProperties(
        BigDecimal maxDeposit, BigDecimal maxWithdrawal, BigDecimal maxTransfer) {

    public LimitsProperties {
        maxDeposit = maxDeposit == null ? new BigDecimal("10000.00") : maxDeposit;
        maxWithdrawal = maxWithdrawal == null ? new BigDecimal("2000.00") : maxWithdrawal;
        maxTransfer = maxTransfer == null ? new BigDecimal("5000.00") : maxTransfer;
        require(maxDeposit, "app.limits.max-deposit");
        require(maxWithdrawal, "app.limits.max-withdrawal");
        require(maxTransfer, "app.limits.max-transfer");
    }

    private static void require(BigDecimal value, String name) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be above 0");
        }
    }
}