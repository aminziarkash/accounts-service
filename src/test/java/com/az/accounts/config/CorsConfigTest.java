package com.az.accounts.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.api.CustomerController;
import com.az.accounts.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
@Import(WebMvcConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=https://app.example.com")
class CorsConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CustomerService customerService;

    @Test
    void allowsAConfiguredOrigin() throws Exception {
        mockMvc.perform(
                        options("/api/v1/customers")
                                .header("Origin", "https://app.example.com")
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string("Access-Control-Allow-Origin", "https://app.example.com"));
    }

    @Test
    void rejectsAnyOtherOrigin() throws Exception {
        mockMvc.perform(
                        options("/api/v1/customers")
                                .header("Origin", "https://evil.example.com")
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
