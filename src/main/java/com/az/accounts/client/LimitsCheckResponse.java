package com.az.accounts.client;

public record LimitsCheckResponse(boolean allowed, String reason) {}
