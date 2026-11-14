package com.az.accounts.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.config.SecurityConfig;
import com.az.accounts.exception.IdempotencyKeyReuseException;
import com.az.accounts.service.AccessPolicy;
import com.az.accounts.service.AccountService;
import com.az.accounts.service.CurrentUser;
import com.az.accounts.service.CustomerService;
import com.az.accounts.service.IdempotentAccountService;
import com.az.accounts.service.IdempotentTransferService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(SecurityConfig.class)
class TransferApiTest {

    private static final String BODY =
            """
            {"fromAccountId":1,"toAccountId":2,"amount":"10.00"}
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AccessPolicy accessPolicy;
    @MockitoBean private CustomerService customerService;
    @MockitoBean private AccountService accountService;
    @MockitoBean private IdempotentTransferService transferService;
    @MockitoBean private IdempotentAccountService idempotentAccountService;

    @Test
    void transferWithKeyReturns201AndTheReference() throws Exception {
        UUID reference = UUID.randomUUID();
        when(transferService.transfer(
                        any(CurrentUser.class),
                        eq("abc-123"),
                        eq(1L),
                        eq(2L),
                        any(BigDecimal.class)))
                .thenReturn(reference);

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header("Idempotency-Key", "abc-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY)
                                .with(jwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferReference").value(reference.toString()));
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY)
                                .with(jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header("Idempotency-Key", " ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY)
                                .with(jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reusingAKeyWithADifferentBodyReturns422() throws Exception {
        when(transferService.transfer(
                        any(CurrentUser.class),
                        eq("abc-123"),
                        eq(1L),
                        eq(2L),
                        any(BigDecimal.class)))
                .thenThrow(new IdempotencyKeyReuseException("abc-123"));

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header("Idempotency-Key", "abc-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY)
                                .with(jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business rule violated"));
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header("Idempotency-Key", "abc-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":\"-1\"}")
                                .with(jwt()))
                .andExpect(status().isBadRequest());
    }
}
