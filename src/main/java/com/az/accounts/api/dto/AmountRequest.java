package com.az.accounts.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AmountRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 15, fraction = 2)
                BigDecimal amount) {}
