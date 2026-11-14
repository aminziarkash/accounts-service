package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.repository.CustomerRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TxDemoService.class, TxDemoHelper.class})
class TransactionRollbackRulesTest {

    @Autowired private TxDemoService demo;
    @Autowired private CustomerRepository customerRepository;

    private static String newEmail() {
        return UUID.randomUUID() + "@test.com";
    }

    @Test
    void uncheckedExceptionRollsBack() {
        String email = newEmail();

        assertThatThrownBy(() -> demo.saveThenThrowUnchecked(email))
                .isInstanceOf(IllegalStateException.class);

        assertThat(customerRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void checkedExceptionStillCommits() {
        String email = newEmail();

        assertThatThrownBy(() -> demo.saveThenThrowChecked(email))
                .isInstanceOf(Exception.class)
                .hasMessage("checked");

        // surprise: the exception was thrown, but the write was committed
        assertThat(customerRepository.findByEmail(email)).isPresent();
    }

    @Test
    void rollbackForMakesCheckedExceptionsRollBack() {
        String email = newEmail();

        assertThatThrownBy(() -> demo.saveThenThrowCheckedWithRollbackFor(email))
                .isInstanceOf(Exception.class);

        assertThat(customerRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void selfInvocationBypassesTheProxyAndNothingRollsBack() {
        String email = newEmail();

        assertThatThrownBy(() -> demo.selfInvocation(email))
                .isInstanceOf(IllegalStateException.class);

        // @Transactional on the inner method was ignored: the save was committed by itself
        assertThat(customerRepository.findByEmail(email)).isPresent();
    }

    @Test
    void callingThroughAnotherBeanRollsBack() {
        String email = newEmail();

        assertThatThrownBy(() -> demo.throughAnotherBean(email))
                .isInstanceOf(IllegalStateException.class);

        assertThat(customerRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void requiresNewSurvivesWhenTheOuterTransactionRollsBack() {
        String outerEmail = newEmail();
        String innerEmail = newEmail();

        assertThatThrownBy(() -> demo.outerFailsAfterInnerRequiresNew(outerEmail, innerEmail))
                .isInstanceOf(IllegalStateException.class);

        assertThat(customerRepository.findByEmail(outerEmail)).isEmpty(); // rolled back
        assertThat(customerRepository.findByEmail(innerEmail)).isPresent(); // survived
    }
}
