package com.az.accounts.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Long accountId, BigDecimal balance, BigDecimal requested) {
        super(
                "Insufficient funds on account "
                        + accountId
                        + ": balance "
                        + balance
                        + ", requested "
                        + requested);
    }
}
