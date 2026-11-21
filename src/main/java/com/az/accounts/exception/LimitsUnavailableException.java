package com.az.accounts.exception;

/**
 * The limits service did not answer in time or failed. The request is refused (503): fail closed.
 */
public class LimitsUnavailableException extends RuntimeException {

    public LimitsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public LimitsUnavailableException(String message) {
        super(message);
    }
}
