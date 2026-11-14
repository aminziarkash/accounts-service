package com.az.accounts.api.error;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.api.mapper.AccountMapperImpl;
import com.az.accounts.domain.Customer;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.DuplicateEmailException;
import com.az.accounts.exception.InsufficientFundsException;
import com.az.accounts.service.AccountService;
import com.az.accounts.service.CustomerService;
import com.az.accounts.service.IdempotentTransferService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(AccountMapperImpl.class)
class ApiErrorHandlingTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IdempotentTransferService transferService;
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
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists());
    }

    @Test
    void negativeDepositReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/accounts/1/deposits")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":\"-5\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')]").exists());
    }

    @Test
    void brokenJsonReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        when(accountService.getAccount(5L)).thenThrow(new AccountNotFoundException(5L));

        mockMvc.perform(get("/api/v1/accounts/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Account not found: 5"));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(customerService.create("a@test.com", "A", "B"))
                .thenThrow(new DuplicateEmailException("a@test.com"));

        mockMvc.perform(
                        post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"email":"a@test.com","firstName":"A","lastName":"B"}
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate resource"));
    }

    @Test
    void optimisticLockFailureReturns409() throws Exception {
        when(customerService.get(1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Customer.class, 1L));

        mockMvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent modification"));
    }

    @Test
    void insufficientFundsReturns422() throws Exception {
        when(accountService.withdraw(1L, new BigDecimal("50.00")))
                .thenThrow(
                        new InsufficientFundsException(
                                1L, new BigDecimal("10.00"), new BigDecimal("50.00")));

        mockMvc.perform(
                        post("/api/v1/accounts/1/withdrawals")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":\"50.00\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business rule violated"));
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingDetails() throws Exception {
        when(customerService.get(1L)).thenThrow(new RuntimeException("db password is hunter2"));

        mockMvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString("hunter2"))));
    }
}
