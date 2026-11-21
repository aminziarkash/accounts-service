package com.az.limits;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class LimitsControllerTest {

    @Autowired private MockMvc mockMvc;

    private ResultActions check(String json) throws Exception {
        return mockMvc.perform(
                post("/api/v1/limits/check").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void anAllowedCheckAnswers200WithAllowedTrue() throws Exception {
        check("{\"accountId\":1,\"currency\":\"USD\",\"amount\":\"100.00\",\"operation\":\"DEPOSIT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void aDeniedCheckIsStillA200WithAReason() throws Exception {
        check("{\"accountId\":1,\"currency\":\"USD\",\"amount\":\"99999.00\",\"operation\":\"WITHDRAW\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").isNotEmpty());
    }

    @Test
    void invalidRequestsAreRejectedWith400() throws Exception {
        check("{\"accountId\":1,\"currency\":\"USD\",\"amount\":\"-5.00\",\"operation\":\"DEPOSIT\"}")
                .andExpect(status().isBadRequest());
        check("{\"accountId\":1,\"currency\":\"USD\",\"amount\":\"5.00\"}")
                .andExpect(status().isBadRequest());
        check("{\"accountId\":1,\"currency\":\"USD\",\"amount\":\"5.00\",\"operation\":\"SWIPE\"}")
                .andExpect(status().isBadRequest());
    }
}