package com.az.accounts.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.domain.LedgerEntryType;
import com.az.accounts.exception.IdempotencyKeyReuseException;
import com.az.accounts.service.AccountService;
import com.az.accounts.service.CustomerService;
import com.az.accounts.service.IdempotentAccountService;
import com.az.accounts.service.IdempotentTransferService;
import com.az.accounts.service.view.LedgerEntryView;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
class AccountIdempotencyApiTest {

    private static final String BODY = "{\"amount\":\"25.00\"}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CustomerService customerService;
    @MockitoBean private AccountService accountService;
    @MockitoBean private IdempotentTransferService transferService;
    @MockitoBean private IdempotentAccountService idempotentAccountService;

    private static LedgerEntryView entry(LedgerEntryType type) {
        return new LedgerEntryView(
                7L, type, new BigDecimal("25.00"), new BigDecimal("125.00"), null, Instant.now());
    }

    @Test
    void depositWithKeyReturns201AndTheLedgerEntry() throws Exception {
        when(idempotentAccountService.deposit(eq("k-1"), eq(1L), any(BigDecimal.class)))
                .thenReturn(entry(LedgerEntryType.DEPOSIT));

        mockMvc.perform(
                        post("/api/v1/accounts/1/deposits")
                                .header("Idempotency-Key", "k-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.type").value("DEPOSIT"));
    }

    @Test
    void withdrawWithKeyReturns201AndTheLedgerEntry() throws Exception {
        when(idempotentAccountService.withdraw(eq("k-2"), eq(1L), any(BigDecimal.class)))
                .thenReturn(entry(LedgerEntryType.WITHDRAWAL));

        mockMvc.perform(
                        post("/api/v1/accounts/1/withdrawals")
                                .header("Idempotency-Key", "k-2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"));
    }

    @Test
    void depositWithoutKeyReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/accounts/1/deposits")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdrawWithBlankKeyReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/accounts/1/withdrawals")
                                .header("Idempotency-Key", " ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'Idempotency-Key')]").exists());
    }

    @Test
    void reusingAKeyWithADifferentBodyReturns422() throws Exception {
        when(idempotentAccountService.deposit(eq("k-1"), eq(1L), any(BigDecimal.class)))
                .thenThrow(new IdempotencyKeyReuseException("k-1"));

        mockMvc.perform(
                        post("/api/v1/accounts/1/deposits")
                                .header("Idempotency-Key", "k-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isUnprocessableEntity());
    }
}
