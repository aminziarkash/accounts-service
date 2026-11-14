package com.az.accounts.api.error;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.config.SecurityConfig;
import com.az.accounts.domain.Customer;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.DuplicateEmailException;
import com.az.accounts.exception.InsufficientFundsException;
import com.az.accounts.exception.InvalidOperationException;
import com.az.accounts.service.AccessPolicy;
import com.az.accounts.service.AccountService;
import com.az.accounts.service.CurrentUser;
import com.az.accounts.service.CustomerService;
import com.az.accounts.service.IdempotentAccountService;
import com.az.accounts.service.IdempotentTransferService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(SecurityConfig.class)
class ApiErrorHandlingTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AccessPolicy accessPolicy;
    @MockitoBean private IdempotentTransferService transferService;
    @MockitoBean private IdempotentAccountService idempotentAccountService;
    @MockitoBean private CustomerService customerService;
    @MockitoBean private AccountService accountService;

    @Test
    void invalidCustomerReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(
                        post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"email":"","firstName":"A","lastName":"B"}
                                        """)
                                .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists());
    }

    @Test
    void negativeDepositReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/accounts/1/deposits")
                                .header("Idempotency-Key", "k-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":\"-5\"}")
                                .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')]").exists());
    }

    @Test
    void brokenJsonReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not json")
                                .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        when(accountService.getAccount(5L)).thenThrow(new AccountNotFoundException(5L));

        mockMvc.perform(get("/api/v1/accounts/5").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Account not found: 5"));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(customerService.create("user", "a@test.com", "A", "B"))
                .thenThrow(new DuplicateEmailException("a@test.com"));

        mockMvc.perform(
                        post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"email":"a@test.com","firstName":"A","lastName":"B"}
                                        """)
                                .with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate resource"));
    }

    @Test
    void optimisticLockFailureReturns409() throws Exception {
        when(customerService.get(1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Customer.class, 1L));

        mockMvc.perform(get("/api/v1/customers/1").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent modification"));
    }

    @Test
    void insufficientFundsReturns422() throws Exception {
        when(idempotentAccountService.withdraw(
                        new CurrentUser("user", false), "k-1", 1L, new BigDecimal("50.00")))
                .thenThrow(
                        new InsufficientFundsException(
                                1L, new BigDecimal("10.00"), new BigDecimal("50.00")));

        mockMvc.perform(
                        post("/api/v1/accounts/1/withdrawals")
                                .header("Idempotency-Key", "k-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":\"50.00\"}")
                                .with(jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business rule violated"));
    }

    @Test
    void businessRuleViolationReturns422() throws Exception {
        when(customerService.get(1L)).thenThrow(new InvalidOperationException("Cannot do that"));

        mockMvc.perform(get("/api/v1/customers/1").with(jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Cannot do that"));
    }

    @Test
    void strayIllegalArgumentExceptionIsOurBugAndReturns500() throws Exception {
        when(customerService.get(1L)).thenThrow(new IllegalArgumentException("library said no"));

        mockMvc.perform(get("/api/v1/customers/1").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void lockTimeoutReturns503WithRetryAfter() throws Exception {
        when(accountService.getAccount(1L))
                .thenThrow(new CannotAcquireLockException("lock timeout"));

        mockMvc.perform(get("/api/v1/accounts/1").with(jwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.title").value("Temporarily busy"));
    }

    @Test
    void accessDeniedReturns403() throws Exception {
        when(accountService.getAccount(1L)).thenThrow(new AccessDeniedException("not yours"));

        mockMvc.perform(get("/api/v1/accounts/1").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingDetails() throws Exception {
        when(customerService.get(1L)).thenThrow(new RuntimeException("db password is hunter2"));

        mockMvc.perform(get("/api/v1/customers/1").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString("hunter2"))));
    }
}
