package com.az.accounts.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.api.CustomerController;
import com.az.accounts.service.AccessPolicy;
import com.az.accounts.service.CustomerService;
import com.az.accounts.service.view.CustomerView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
class SecurityRulesTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AccessPolicy accessPolicy;
    @MockitoBean private CustomerService customerService;

    @Test
    void requestWithoutTokenGets401AsProblemJson() throws Exception {
        mockMvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void postWithoutTokenGets401NotACsrfError() throws Exception {
        mockMvc.perform(
                        post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenGets401() throws Exception {
        mockMvc.perform(get("/api/v1/customers/1").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        when(customerService.get(1L))
                .thenReturn(new CustomerView(1L, "a@test.com", "A", "B", List.of()));

        mockMvc.perform(get("/api/v1/customers/1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void publicPathsNeedNoToken() throws Exception {
        // not 401: security lets it through (this slice has no actuator, so MVC answers 404)
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    @Test
    void unknownPathsAreDeniedByDefault() throws Exception {
        mockMvc.perform(get("/internal/secret").with(jwt())).andExpect(status().isForbidden());
    }
}
