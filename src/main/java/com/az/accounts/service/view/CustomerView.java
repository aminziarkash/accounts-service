package com.az.accounts.service.view;

import java.util.List;

public record CustomerView(
        Long id, String email, String firstName, String lastName, List<AccountView> accounts) {}
