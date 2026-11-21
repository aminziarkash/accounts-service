package com.az.accounts.exception;

/** The limits service said no (answered with 422, like any business rule). */
public class LimitExceededException extends RuntimeException {

    public LimitExceededException(String reason) {
        super(reason);
    }
}
