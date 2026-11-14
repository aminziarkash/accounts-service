package com.az.accounts.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is calling: the stable Keycloak id (the token's "sub") and whether they are staff.
 *
 * @param subject the "sub" claim
 * @param admin true when the token carries the ADMIN realm role
 */
public record CurrentUser(String subject, boolean admin) {

    public static CurrentUser from(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Expected a JWT principal");
        }
        boolean admin =
                authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new CurrentUser(jwt.getSubject(), admin);
    }
}
