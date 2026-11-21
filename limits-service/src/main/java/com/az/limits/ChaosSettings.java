package com.az.limits;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChaosSettings(
        @NotNull @PositiveOrZero @Max(60000) Long delayMillis, @NotNull Boolean failing) {}