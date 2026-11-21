package com.az.limits;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class LimitsService {

    private final LimitsProperties properties;

    public LimitsService(LimitsProperties properties) {
        this.properties = properties;
    }

    public CheckResponse check(CheckRequest request) {
        BigDecimal max =
                switch (request.operation()) {
                    case DEPOSIT -> properties.maxDeposit();
                    case WITHDRAW -> properties.maxWithdrawal();
                    case TRANSFER -> properties.maxTransfer();
                };
        // compareTo, not equals: 5000.00 and 5000 are the same amount
        if (request.amount().compareTo(max) > 0) {
            return CheckResponse.deny(
                    request.operation() + " above the limit of " + max.toPlainString());
        }
        return CheckResponse.approve();
    }
}