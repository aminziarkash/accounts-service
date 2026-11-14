package com.az.accounts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param issuerUri the "iss" every token must carry, for example the Keycloak realm URL
 * @param jwkSetUri where the public keys that verify the token signature are fetched from
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String issuerUri, String jwkSetUri) {}
