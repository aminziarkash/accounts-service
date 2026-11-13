package com.az.accounts.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.domain.Customer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CustomerOptimisticLockingTest {

    @Autowired private CustomerRepository customerRepository;

    @Test
    void secondStaleUpdateFailsInsteadOfOverwriting() {
        Long id =
                customerRepository
                        .save(new Customer(UUID.randomUUID() + "@test.com", "Old", "Name"))
                        .getId();

        // two users open the same profile (open-in-view is off, so these are detached copies)
        Customer tabA = customerRepository.findById(id).orElseThrow();
        Customer tabB = customerRepository.findById(id).orElseThrow();
        assertThat(tabA.getVersion()).isEqualTo(tabB.getVersion());

        // tab A saves first: succeeds, version goes up
        tabA.changeName("Anna", "Smith");
        customerRepository.save(tabA);
        assertThat(customerRepository.findById(id).orElseThrow().getVersion())
                .isEqualTo(tabA.getVersion() + 1);

        // tab B still holds the old version: its save must be rejected
        tabB.changeName("Bob", "Jones");
        assertThatThrownBy(() -> customerRepository.save(tabB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // and A's change was not overwritten
        Customer stored = customerRepository.findById(id).orElseThrow();
        assertThat(stored.getFirstName()).isEqualTo("Anna");
        assertThat(stored.getLastName()).isEqualTo("Smith");
    }
}
