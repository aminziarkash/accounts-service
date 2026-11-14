package com.az.accounts.service;

import com.az.accounts.domain.Customer;
import com.az.accounts.exception.CustomerNotFoundException;
import com.az.accounts.exception.DuplicateEmailException;
import com.az.accounts.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer create(String email, String firstName, String lastName) {
        if (customerRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException(email);
        }
        return customerRepository.save(new Customer(email, firstName, lastName));
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customerRepository
                .findWithAccountsById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
