package com.az.limits;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "app.chaos.enabled=true")
@AutoConfigureMockMvc
class ChaosApiTest {

    private static final String CHECK =
            "{\"accountId\":1,\"currency\":\"USD\",\"amount\":\"100.00\",\"operation\":\"TRANSFER\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ChaosState chaos;

    @AfterEach
    void reset() {
        chaos.reset();
    }

    private ResultActions setChaos(String json) throws Exception {
        return mockMvc.perform(
                put("/admin/chaos").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions check() throws Exception {
        return mockMvc.perform(
                post("/api/v1/limits/check").contentType(MediaType.APPLICATION_JSON).content(CHECK));
    }

    @Test
    void failingMakesTheCheckAnswer503UntilItIsReset() throws Exception {
        setChaos("{\"delayMillis\":0,\"failing\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failing").value(true));

        check().andExpect(status().isServiceUnavailable());

        mockMvc.perform(delete("/admin/chaos")).andExpect(status().isNoContent());

        check().andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void theSettingsCanBeRead() throws Exception {
        setChaos("{\"delayMillis\":250,\"failing\":false}").andExpect(status().isOk());

        mockMvc.perform(get("/admin/chaos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayMillis").value(250))
                .andExpect(jsonPath("$.failing").value(false));
    }

    @Test
    void invalidSettingsAreRejected() throws Exception {
        setChaos("{\"delayMillis\":-1,\"failing\":false}").andExpect(status().isBadRequest());
        setChaos("{\"delayMillis\":999999,\"failing\":false}").andExpect(status().isBadRequest());
        setChaos("{\"failing\":true}").andExpect(status().isBadRequest());
    }

    @Test
    void healthStaysUpWhileTheCheckFails() throws Exception {
        setChaos("{\"delayMillis\":0,\"failing\":true}").andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}