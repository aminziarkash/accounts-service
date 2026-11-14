package com.az.accounts.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OpenAccountRequest(
        @NotNull @Positive Long customerId, @NotBlank @CurrencyCode String currency) {}
