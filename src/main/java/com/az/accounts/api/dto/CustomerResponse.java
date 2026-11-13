package com.az.accounts.api.dto;

import java.util.List;

public record CustomerResponse(
        Long id, String email, String firstName, String lastName, List<AccountResponse> accounts) {}
