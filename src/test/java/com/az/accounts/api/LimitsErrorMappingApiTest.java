package com.az.accounts.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.config.SecurityConfig;
import com.az.accounts.exception.LimitExceededException;
import com.az.accounts.exception.LimitsUnavailableException;
import com.az.accounts.service.AccessPolicy;
import com.az.accounts.service.AccountService;
import com.az.accounts.service.CurrentUser;
import com.az.accounts.service.CustomerService;
import com.az.accounts.service.IdempotentAccountService;
import com.az.accounts.service.IdempotentTransferService;
import com.az.accounts.service.RateLimiter;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest
@Import(SecurityConfig.class)
class LimitsErrorMappingApiTest {

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
    @MockitoBean private RateLimiter rateLimiter;

    private ResultActions transfer() throws Exception {
        return mockMvc.perform(
                post("/api/v1/transfers")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(jwt()));
    }

    @Test
    void limitsServiceDownIs503WithRetryAfterAndNoDetailsLeaked() throws Exception {
        when(transferService.transfer(
                        any(CurrentUser.class),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        any(BigDecimal.class)))
                .thenThrow(new LimitsUnavailableException("connect refused to localhost:8100"));

        transfer()
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.title").value("Limits check unavailable"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString("8100"))));
    }

    @Test
    void limitDeniedIs422WithTheReason() throws Exception {
        when(transferService.transfer(
                        any(CurrentUser.class),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        any(BigDecimal.class)))
                .thenThrow(new LimitExceededException("TRANSFER above the limit of 5000.00"));

        transfer()
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business rule violated"))
                .andExpect(jsonPath("$.detail").value("TRANSFER above the limit of 5000.00"));
    }
}
