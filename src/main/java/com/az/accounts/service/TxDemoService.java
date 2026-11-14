package com.az.accounts.service;

import com.az.accounts.domain.Customer;
import com.az.accounts.repository.CustomerRepository;
import org.springframework.transaction.annotation.Transactional;

public class TxDemoService {

    private final CustomerRepository customerRepository;
    private final TxDemoHelper helper;

    public TxDemoService(CustomerRepository customerRepository, TxDemoHelper helper) {
        this.customerRepository = customerRepository;
        this.helper = helper;
    }

    // --- rollback rules ---

    @Transactional
    public void saveThenThrowUnchecked(String email) {
        customerRepository.save(new Customer(email, "Tx", "Demo"));
        throw new IllegalStateException("unchecked");
    }

    @Transactional
    public void saveThenThrowChecked(String email) throws Exception {
        customerRepository.save(new Customer(email, "Tx", "Demo"));
        throw new Exception("checked");
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveThenThrowCheckedWithRollbackFor(String email) throws Exception {
        customerRepository.save(new Customer(email, "Tx", "Demo"));
        throw new Exception("checked, but rollbackFor is set");
    }

    // --- self-invocation ---

    /** No @Transactional here, and it calls a @Transactional method of the same class. */
    public void selfInvocation(String email) {
        saveThenThrowUnchecked(email); // this.method(): the proxy is bypassed
    }

    /** Same idea, but the call goes through another bean, so the proxy applies. */
    public void throughAnotherBean(String email) {
        helper.saveThenThrowUnchecked(email);
    }

    // --- REQUIRES_NEW ---

    @Transactional
    public void outerFailsAfterInnerRequiresNew(String outerEmail, String innerEmail) {
        customerRepository.save(new Customer(outerEmail, "Tx", "Outer"));
        helper.saveInNewTransaction(innerEmail); // commits on its own
        throw new IllegalStateException("outer fails after inner committed");
    }
}
