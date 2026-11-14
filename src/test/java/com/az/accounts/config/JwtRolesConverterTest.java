package com.az.accounts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRolesConverterTest {

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("7d1c0c3e")
                .claim("preferred_username", "alice");
    }

    @Test
    void keycloakRealmRolesBecomeSpringRoles() {
        Jwt jwt = token().claim("realm_access", Map.of("roles", List.of("USER", "ADMIN"))).build();

        var authentication = SecurityConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void usernameIsThePrincipalName() {
        var authentication = SecurityConfig.jwtAuthenticationConverter().convert(token().build());

        assertThat(authentication.getName()).isEqualTo("alice");
    }

    @Test
    void tokenWithoutRolesGetsNoAuthorities() {
        var authentication = SecurityConfig.jwtAuthenticationConverter().convert(token().build());

        assertThat(authentication.getAuthorities()).isEmpty();
    }
}
