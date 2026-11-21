package com.az.accounts.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(LimitsClientProperties.class)
public class LimitsClientConfig {

    @Bean
    public RestClient limitsRestClient(com.az.accounts.config.LimitsClientProperties properties) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .version(
                                HttpClient.Version
                                        .HTTP_1_1) // HTTP_1_1 is deliberate: it avoids the HTTP/2
                        // upgrade attempt that the JDK client makes on
                        // plain HTTP, which can confuse simple servers.
                        .connectTimeout(properties.connectTimeout())
                        .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
