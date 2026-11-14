package com.az.accounts.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.config.SecurityConfig;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.service.AccessPolicy;
import com.az.accounts.service.AccountService;
import com.az.accounts.service.CurrentUser;
import com.az.accounts.service.CustomerService;
import com.az.accounts.service.IdempotentAccountService;
import com.az.accounts.service.IdempotentTransferService;
import com.az.accounts.service.view.CustomerView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Checks the wiring: controllers pass the right caller to the policy and stop when it says no. */
@WebMvcTest
@Import(SecurityConfig.class)
class AccessControlApiTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AccessPolicy accessPolicy;
    @MockitoBean private CustomerService customerService;
    @MockitoBean private AccountService accountService;
    @MockitoBean private IdempotentTransferService transferService;
    @MockitoBean private IdempotentAccountService idempotentAccountService;

    @Test
    void newCustomersAreOwnedByTheCallerFromTheToken() throws Exception {
        when(customerService.create(any(), any(), any(), any()))
                .thenReturn(new CustomerView(1L, "a@test.com", "A", "B", List.of()));

        mockMvc.perform(
                        post("/api/v1/customers")
                                .with(jwt().jwt(j -> j.subject("alice-sub")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"a@test.com\",\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andExpect(status().isCreated());

        verify(customerService).create(eq("alice-sub"), eq("a@test.com"), eq("A"), eq("B"));
    }

    @Test
    void thePolicyGetsTheSubjectAndTheAdminFlag() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/5").with(jwt().jwt(j -> j.subject("alice-sub"))))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/accounts/5")
                                .with(
                                        jwt().jwt(j -> j.subject("admin-sub"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        ArgumentCaptor<CurrentUser> users = ArgumentCaptor.forClass(CurrentUser.class);
        verify(accessPolicy, org.mockito.Mockito.times(2)).requireAccount(users.capture(), eq(5L));
        assertThat(users.getAllValues())
                .containsExactly(new CurrentUser("alice-sub", false), new CurrentUser("admin-sub", true));
    }

    @Test
    void whenThePolicySaysNoTheAnswerIs404AndNothingElseRuns() throws Exception {
        doThrow(new AccountNotFoundException(5L))
                .when(accessPolicy)
                .requireAccount(any(CurrentUser.class), eq(5L));

        mockMvc.perform(
                        post("/api/v1/accounts/5/deposits")
                                .with(jwt())
                                .header("Idempotency-Key", "k-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":\"10.00\"}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(idempotentAccountService);
    }

    @Test
    void transfersAreCheckedAgainstTheSourceAccount() throws Exception {
        doThrow(new AccountNotFoundException(1L))
                .when(accessPolicy)
                .requireAccount(any(CurrentUser.class), eq(1L));

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .with(jwt())
                                .header("Idempotency-Key", "k-2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":\"10.00\"}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(transferService);
    }
}
