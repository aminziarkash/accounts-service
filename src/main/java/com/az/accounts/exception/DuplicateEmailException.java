package com.az.accounts.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String email) {
        super("A customer with this email already exists: " + email);
    }
}
