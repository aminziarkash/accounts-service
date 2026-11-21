package com.az.limits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CheckRequest(
        @NotNull Long accountId,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @Positive BigDecimal amount,
        @NotNull Operation operation) {}