package com.az.accounts.service;

import com.az.accounts.domain.Customer;
import com.az.accounts.repository.CustomerRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class TxDemoHelper {

    private final CustomerRepository customerRepository;

    public TxDemoHelper(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public void saveThenThrowUnchecked(String email) {
        customerRepository.save(new Customer(email, "Tx", "Demo"));
        throw new IllegalStateException("unchecked");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveInNewTransaction(String email) {
        customerRepository.save(new Customer(email, "Tx", "Demo"));
    }
}
