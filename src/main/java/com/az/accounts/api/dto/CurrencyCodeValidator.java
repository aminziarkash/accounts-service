package com.az.accounts.api.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;

public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null is the job of @NotNull, so each rule does one thing
        }
        try {
            Currency.getInstance(value); // case sensitive: "usd" is rejected
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
