package com.az.accounts.exception;

/** The request is well-formed but breaks a business rule the caller can fix (answered with 422). */
public class InvalidOperationException extends RuntimeException {

    public InvalidOperationException(String message) {
        super(message);
    }

    public InvalidOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
