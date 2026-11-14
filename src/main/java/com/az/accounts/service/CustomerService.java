package com.az.accounts.service;

import com.az.accounts.domain.Customer;
import com.az.accounts.exception.CustomerNotFoundException;
import com.az.accounts.exception.DuplicateEmailException;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.service.view.CustomerView;
import com.az.accounts.service.view.ViewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final ViewMapper mapper;

    public CustomerService(CustomerRepository customerRepository, ViewMapper mapper) {
        this.customerRepository = customerRepository;
        this.mapper = mapper;
    }

    @Transactional
    public CustomerView create(
            String ownerSubject, String email, String firstName, String lastName) {
        if (customerRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException(email);
        }
        return mapper.toView(customerRepository.save(new Customer(ownerSubject, email, firstName, lastName)));
    }

    @Transactional(readOnly = true)
    public CustomerView get(Long id) {
        return mapper.toView(
                customerRepository
                        .findWithAccountsById(id)
                        .orElseThrow(() -> new CustomerNotFoundException(id)));
    }
}
