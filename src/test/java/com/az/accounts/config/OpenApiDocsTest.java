package com.az.accounts.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocsTest {

    @Autowired private MockMvc mockMvc;

    private ResultActions docs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void apiDocsHaveTheTitleAndAllResources() throws Exception {
        docs().andExpect(jsonPath("$.info.title").value("accounts-service API"))
                .andExpect(jsonPath("$.paths['/api/v1/customers']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{id}/deposits']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{id}/withdrawals']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{id}/ledger']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transfers']").exists());
    }

    @Test
    void endpointsAreGroupedByTag() throws Exception {
        docs().andExpect(
                        jsonPath("$.paths['/api/v1/customers'].post.tags")
                                .value(hasItem("Customers")))
                .andExpect(
                        jsonPath("$.paths['/api/v1/accounts'].post.tags")
                                .value(hasItem("Accounts")))
                .andExpect(
                        jsonPath("$.paths['/api/v1/transfers'].post.tags")
                                .value(hasItem("Transfers")));
    }

    @Test
    void everyEndpointHasASummary() throws Exception {
        String body = docs().andReturn().getResponse().getContentAsString();

        List<?> operations = JsonPath.read(body, "$.paths.*.*");
        List<?> summaries = JsonPath.read(body, "$.paths.*.*.summary");

        assertThat(operations).isNotEmpty();
        assertThat(summaries).hasSameSizeAs(operations);
    }

    @Test
    void errorResponsesAreDocumented() throws Exception {
        docs().andExpect(jsonPath("$.paths['/api/v1/customers'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers'].post.responses['409']").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/customers/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts'].post.responses['404']").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/accounts/{id}/deposits'].post.responses['400']")
                                .exists())
                .andExpect(
                        jsonPath(
                                        "$.paths['/api/v1/accounts/{id}/withdrawals'].post.responses['422']")
                                .exists())
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses['422']").exists());
    }

    @Test
    void transferDocumentsItsIdempotencyHeader() throws Exception {
        docs().andExpect(jsonPath("$.paths['/api/v1/transfers'].post").exists())
                .andExpect(
                        jsonPath(
                                        "$.paths['/api/v1/transfers'].post.parameters[?(@.name=='Idempotency-Key')]")
                                .exists());
    }

    @Test
    void ledgerDocumentsItsPagingAndSortingParameters() throws Exception {
        String ledger = "$.paths['/api/v1/accounts/{id}/ledger'].get.parameters";

        docs().andExpect(jsonPath(ledger + "[?(@.name=='page')]").exists())
                .andExpect(
                        jsonPath(ledger + "[?(@.name=='size')].description")
                                .value(hasItem("Page size, 1 to 100")))
                .andExpect(
                        jsonPath(ledger + "[?(@.name=='sort')].description")
                                .value(hasItem("Sort field: createdAt or amount")))
                .andExpect(jsonPath(ledger + "[?(@.name=='direction')]").exists());
    }
}
