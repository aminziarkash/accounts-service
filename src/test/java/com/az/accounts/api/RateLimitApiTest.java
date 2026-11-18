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

import com.az.accounts.config.ClockConfig;
import com.az.accounts.config.SecurityConfig;
import com.az.accounts.service.AccessPolicy;
import com.az.accounts.service.AccountService;
import com.az.accounts.service.CurrentUser;
import com.az.accounts.service.CustomerService;
import com.az.accounts.service.IdempotentAccountService;
import com.az.accounts.service.IdempotentTransferService;
import com.az.accounts.service.RateLimiter;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// The limiter is real here (the other tests switch it off): a bucket of 2 that practically never
// refills, so the third write in a row must be refused.
@WebMvcTest(
        properties = {
            "app.rate-limit.enabled=true",
            "app.rate-limit.capacity=2",
            "app.rate-limit.refill-per-second=0.001"
        })
@Import({SecurityConfig.class, RateLimiter.class, ClockConfig.class})
class RateLimitApiTest {

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

    @BeforeEach
    void stubTransfer() {
        when(transferService.transfer(
                        any(CurrentUser.class),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        any(BigDecimal.class)))
                .thenReturn(UUID.randomUUID());
    }

    private ResultActions transferAs(String subject) throws Exception {
        return mockMvc.perform(
                post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(jwt().jwt(j -> j.subject(subject))));
    }

    @Test
    void thirdWriteInARowGets429WithRetryAfter() throws Exception {
        String alice = "alice-" + UUID.randomUUID();

        transferAs(alice).andExpect(status().isCreated());
        transferAs(alice).andExpect(status().isCreated());

        transferAs(alice)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.title").value("Too many requests"));
    }

    @Test
    void oneCallerBeingBlockedDoesNotBlockAnother() throws Exception {
        String alice = "alice-" + UUID.randomUUID();
        String bob = "bob-" + UUID.randomUUID();

        transferAs(alice).andExpect(status().isCreated());
        transferAs(alice).andExpect(status().isCreated());
        transferAs(alice).andExpect(status().isTooManyRequests());

        transferAs(bob).andExpect(status().isCreated());
    }
}
