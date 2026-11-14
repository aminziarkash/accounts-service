package com.az.accounts.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI accountsOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("accounts-service API")
                                .version("v1")
                                .description(
                                        "Customers, accounts, ledger and idempotent transfers. "
                                                + "Errors use RFC 9457 problem details."));
    }
}
