package com.az.accounts.service;

import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.CustomerNotFoundException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import org.springframework.stereotype.Component;

/**
 * The one place that decides who may touch what. A valid token only proves who you are, so every
 * request for a customer or an account also checks that it is yours (admins may see everything).
 *
 * <p>Someone else's resource answers "not found", the same as a resource that does not exist, so
 * nobody can find out which ids are in use.
 */
@Component
public class AccessPolicy {

    private final CustomerRepository customers;
    private final AccountRepository accounts;

    public AccessPolicy(CustomerRepository customers, AccountRepository accounts) {
        this.customers = customers;
        this.accounts = accounts;
    }

    public void requireCustomer(CurrentUser user, Long customerId) {
        if (!user.admin() && !customers.existsByIdAndOwnerSubject(customerId, user.subject())) {
            throw new CustomerNotFoundException(customerId);
        }
    }

    public void requireAccount(CurrentUser user, Long accountId) {
        if (!user.admin()
                && !accounts.existsByIdAndCustomerOwnerSubject(accountId, user.subject())) {
            throw new AccountNotFoundException(accountId);
        }
    }
}
