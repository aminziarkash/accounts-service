package com.az.limits;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.chaos.enabled=false")
@AutoConfigureMockMvc
class ChaosDisabledTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void theAdminEndpointDoesNotExistWhenTheSwitchIsOff() throws Exception {
        mockMvc.perform(
                        put("/admin/chaos")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"delayMillis\":0,\"failing\":true}"))
                .andExpect(status().isNotFound());
    }
}